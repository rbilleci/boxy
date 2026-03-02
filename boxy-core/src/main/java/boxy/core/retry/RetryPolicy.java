package boxy.core.retry;

import boxy.core.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Configurable retry policy with exponential backoff and jitter for transient
 * database failures (item #111).
 *
 * <p>Only {@link DataAccessException}s where {@link DataAccessException#isRetryable()} returns
 * {@code true} are retried.  Fatal errors (constraint violations, SIGNAL exceptions, etc.)
 * propagate immediately without retry.
 *
 * <p>Backoff formula (full-jitter):
 * <pre>
 *   sleepMs = random(0, min(maxBackoffMs, initialBackoffMs * 2^attempt))
 * </pre>
 * Full jitter avoids thundering-herd problems on pool exhaustion or deadlock storms.
 *
 * <p>Usage example:
 * <pre>{@code
 * RetryPolicy retry = RetryPolicy.defaults();
 * retry.execute(() -> eventRepo.publish(path, topic, key, data));
 * }</pre>
 *
 * <p>Or with a return value:
 * <pre>{@code
 * RetryPolicy retry = RetryPolicy.builder()
 *     .maxAttempts(5)
 *     .initialBackoffMs(50)
 *     .maxBackoffMs(2000)
 *     .build();
 * List<Event> events = retry.execute(() -> pollAndCommit(consumerId));
 * }</pre>
 */
public final class RetryPolicy {

    private static final Logger log = LoggerFactory.getLogger(RetryPolicy.class);

    /** Default maximum number of attempts (1 original + 2 retries). */
    public static final int DEFAULT_MAX_ATTEMPTS = 3;

    /** Default initial backoff before first retry (ms). */
    public static final long DEFAULT_INITIAL_BACKOFF_MS = 50L;

    /** Default maximum backoff cap (ms). */
    public static final long DEFAULT_MAX_BACKOFF_MS = 2_000L;

    private final int  maxAttempts;
    private final long initialBackoffMs;
    private final long maxBackoffMs;

    private RetryPolicy(final int maxAttempts,
                        final long initialBackoffMs,
                        final long maxBackoffMs) {
        this.maxAttempts      = maxAttempts;
        this.initialBackoffMs = initialBackoffMs;
        this.maxBackoffMs     = maxBackoffMs;
    }

    /**
     * Returns the default retry policy: 3 total attempts, 50ms–2000ms backoff.
     *
     * @return a new {@link RetryPolicy} with default settings
     */
    public static RetryPolicy defaults() {
        return new RetryPolicy(DEFAULT_MAX_ATTEMPTS, DEFAULT_INITIAL_BACKOFF_MS, DEFAULT_MAX_BACKOFF_MS);
    }

    /**
     * Returns a {@link Builder} for customising retry settings.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Executes a no-return operation, retrying on retryable {@link DataAccessException}.
     *
     * @param action the action to execute
     * @throws DataAccessException if all attempts fail, or if the failure is not retryable
     * @throws InterruptedException if interrupted during a backoff sleep
     */
    public void execute(final Runnable action) throws InterruptedException {
        execute(() -> { action.run(); return null; });
    }

    /**
     * Executes a value-returning operation, retrying on retryable {@link DataAccessException}.
     *
     * @param <T>    the return type
     * @param action the action to execute
     * @return the result of the action
     * @throws DataAccessException  if all attempts fail, or if the failure is not retryable
     * @throws InterruptedException if interrupted during a backoff sleep
     */
    public <T> T execute(final Supplier<T> action) throws InterruptedException {
        DataAccessException lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return action.get();
            } catch (final DataAccessException e) {
                if (!e.isRetryable()) {
                    log.debug("Non-retryable error on attempt {}/{}: sqlState={} vendorCode={}",
                            attempt, maxAttempts, e.getSqlState(), e.getVendorCode());
                    throw e;
                }
                lastException = e;
                if (attempt < maxAttempts) {
                    final long backoffMs = computeBackoff(attempt);
                    log.warn("Retryable error on attempt {}/{} (retrying in {}ms): sqlState={} vendorCode={}",
                            attempt, maxAttempts, backoffMs, e.getSqlState(), e.getVendorCode());
                    Thread.sleep(backoffMs);
                }
            }
        }

        log.warn("All {} attempts exhausted", maxAttempts);
        throw lastException;
    }

    /**
     * Computes the sleep duration for a given attempt using full-jitter exponential backoff.
     *
     * @param attempt 1-based attempt number (1 = first retry)
     * @return sleep duration in milliseconds
     */
    private long computeBackoff(final int attempt) {
        final long ceiling = Math.min(maxBackoffMs, initialBackoffMs * (1L << attempt));
        return ThreadLocalRandom.current().nextLong(0, ceiling + 1);
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    /** Builder for {@link RetryPolicy}. */
    public static final class Builder {
        private int  maxAttempts      = DEFAULT_MAX_ATTEMPTS;
        private long initialBackoffMs = DEFAULT_INITIAL_BACKOFF_MS;
        private long maxBackoffMs     = DEFAULT_MAX_BACKOFF_MS;

        private Builder() {}

        /**
         * Sets the maximum total number of attempts (including the first attempt).
         *
         * @param maxAttempts must be ≥ 1
         * @return this builder
         */
        public Builder maxAttempts(final int maxAttempts) {
            if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be >= 1");
            this.maxAttempts = maxAttempts;
            return this;
        }

        /**
         * Sets the initial backoff in milliseconds before the first retry.
         *
         * @param initialBackoffMs must be ≥ 0
         * @return this builder
         */
        public Builder initialBackoffMs(final long initialBackoffMs) {
            if (initialBackoffMs < 0) throw new IllegalArgumentException("initialBackoffMs must be >= 0");
            this.initialBackoffMs = initialBackoffMs;
            return this;
        }

        /**
         * Sets the maximum backoff cap in milliseconds.
         *
         * @param maxBackoffMs must be ≥ 0
         * @return this builder
         */
        public Builder maxBackoffMs(final long maxBackoffMs) {
            if (maxBackoffMs < 0) throw new IllegalArgumentException("maxBackoffMs must be >= 0");
            this.maxBackoffMs = maxBackoffMs;
            return this;
        }

        /** Builds the {@link RetryPolicy}. */
        public RetryPolicy build() {
            return new RetryPolicy(maxAttempts, initialBackoffMs, maxBackoffMs);
        }
    }
}
