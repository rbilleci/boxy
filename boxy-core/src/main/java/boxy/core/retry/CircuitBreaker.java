package boxy.core.retry;

import boxy.core.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Simple circuit breaker for Boxy stored-procedure calls (item #112).
 *
 * <p>Prevents cascade failures by blocking calls when the database is detected to be
 * unavailable, giving it time to recover before allowing traffic through again.
 *
 * <p>State machine:
 * <pre>
 *   CLOSED ──(threshold failures)──▶ OPEN
 *   OPEN   ──(resetTimeoutMs elapsed)──▶ HALF_OPEN
 *   HALF_OPEN ──(success)──▶ CLOSED
 *   HALF_OPEN ──(failure)──▶ OPEN (reset timer)
 * </pre>
 *
 * <p>Only {@link DataAccessException}s where {@link DataAccessException#isRetryable()} returns
 * {@code true} increment the failure counter.  Fatal errors (constraint violations, SIGNAL
 * codes) do not open the circuit — they indicate caller bugs, not infrastructure problems.
 *
 * <p>Usage:
 * <pre>{@code
 * CircuitBreaker cb = CircuitBreaker.defaults("boxy-db");
 * try {
 *     cb.execute(() -> eventRepo.publish(path, topic, key, data));
 * } catch (CircuitBreaker.OpenException e) {
 *     // database appears unavailable; retry later
 * }
 * }</pre>
 */
public final class CircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreaker.class);

    /** Default failure threshold before opening the circuit. */
    public static final int DEFAULT_FAILURE_THRESHOLD = 5;

    /** Default time (ms) to wait in OPEN state before attempting a probe. */
    public static final long DEFAULT_RESET_TIMEOUT_MS = 10_000L;

    // -------------------------------------------------------------------------
    // Circuit state
    // -------------------------------------------------------------------------

    /** Possible states of the circuit. */
    public enum State { CLOSED, OPEN, HALF_OPEN }

    /** Thrown when a call is rejected because the circuit is OPEN. */
    public static final class OpenException extends RuntimeException {
        public OpenException(final String name) {
            super("Circuit breaker '" + name + "' is OPEN — call rejected");
        }
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final String            name;
    private final int               failureThreshold;
    private final long              resetTimeoutMs;

    private final AtomicReference<State> state          = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger          failureCount   = new AtomicInteger(0);
    private final AtomicLong             openedAtMs     = new AtomicLong(0);

    private CircuitBreaker(final String name,
                           final int failureThreshold,
                           final long resetTimeoutMs) {
        this.name             = name;
        this.failureThreshold = failureThreshold;
        this.resetTimeoutMs   = resetTimeoutMs;
    }

    /**
     * Creates a circuit breaker with default settings.
     *
     * @param name a descriptive name (e.g. {@code "boxy-db"}) used in log messages
     * @return a new {@link CircuitBreaker}
     */
    public static CircuitBreaker defaults(final String name) {
        return new CircuitBreaker(name, DEFAULT_FAILURE_THRESHOLD, DEFAULT_RESET_TIMEOUT_MS);
    }

    /**
     * Creates a circuit breaker with custom thresholds.
     *
     * @param name             descriptive name for log messages
     * @param failureThreshold number of retryable failures before opening (must be ≥ 1)
     * @param resetTimeoutMs   milliseconds to wait in OPEN state before probing (must be ≥ 0)
     * @return a new {@link CircuitBreaker}
     */
    public static CircuitBreaker of(final String name,
                                    final int failureThreshold,
                                    final long resetTimeoutMs) {
        return new CircuitBreaker(name, failureThreshold, resetTimeoutMs);
    }

    // -------------------------------------------------------------------------
    // Execution
    // -------------------------------------------------------------------------

    /**
     * Executes the given action through this circuit breaker.
     *
     * @param action the action to execute
     * @throws OpenException          if the circuit is currently OPEN
     * @throws DataAccessException    if the action throws a database error
     */
    public void execute(final Runnable action) {
        execute(() -> { action.run(); return null; });
    }

    /**
     * Executes the given value-returning action through this circuit breaker.
     *
     * @param <T>    the return type
     * @param action the action to execute
     * @return the result of the action
     * @throws OpenException       if the circuit is currently OPEN
     * @throws DataAccessException if the action throws a database error
     */
    public <T> T execute(final Supplier<T> action) {
        final State currentState = evaluateState();
        if (currentState == State.OPEN) {
            throw new OpenException(name);
        }

        try {
            final T result = action.get();
            onSuccess(currentState);
            return result;
        } catch (final DataAccessException e) {
            if (e.isRetryable()) {
                onFailure();
            }
            throw e;
        }
    }

    /** Returns the current state of the circuit. */
    public State getState() {
        return evaluateState();
    }

    /** Returns the current failure count. */
    public int getFailureCount() {
        return failureCount.get();
    }

    // -------------------------------------------------------------------------
    // State management
    // -------------------------------------------------------------------------

    private State evaluateState() {
        final State s = state.get();
        if (s == State.OPEN) {
            final long elapsed = System.currentTimeMillis() - openedAtMs.get();
            if (elapsed >= resetTimeoutMs) {
                if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                    log.info("Circuit breaker '{}' → HALF_OPEN (probing after {}ms)", name, elapsed);
                }
                return State.HALF_OPEN;
            }
        }
        return state.get();
    }

    private void onSuccess(final State stateBeforeCall) {
        if (stateBeforeCall == State.HALF_OPEN) {
            failureCount.set(0);
            state.set(State.CLOSED);
            log.info("Circuit breaker '{}' → CLOSED (probe succeeded)", name);
        } else {
            failureCount.set(0);
        }
    }

    private void onFailure() {
        final int failures = failureCount.incrementAndGet();
        if (failures >= failureThreshold && state.compareAndSet(State.CLOSED, State.OPEN)) {
            openedAtMs.set(System.currentTimeMillis());
            log.warn("Circuit breaker '{}' → OPEN after {} retryable failures", name, failures);
        } else if (state.get() == State.HALF_OPEN) {
            state.set(State.OPEN);
            openedAtMs.set(System.currentTimeMillis());
            log.warn("Circuit breaker '{}' → OPEN (probe failed)", name);
        }
    }
}
