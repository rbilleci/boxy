package boxy.core.retry;

import boxy.core.DataAccessException;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link RetryPolicy}.
 */
class RetryPolicyTest {

    private static DataAccessException retryableError() {
        return new DataAccessException(new SQLException("Deadlock", "40001", 1213));
    }

    private static DataAccessException fatalError() {
        return new DataAccessException(new SQLException("UNKNOWN_CONSUMER", "45000", 0));
    }

    // -------------------------------------------------------------------------
    // Success on first attempt
    // -------------------------------------------------------------------------

    @Test
    void execute_successOnFirstAttempt_returnsResult() throws InterruptedException {
        var retry = RetryPolicy.defaults();
        var result = retry.execute(() -> "hello");
        assertThat(result).isEqualTo("hello");
    }

    @Test
    void executeRunnable_successOnFirstAttempt() throws InterruptedException {
        var retry = RetryPolicy.defaults();
        var counter = new AtomicInteger(0);
        retry.execute(() -> counter.incrementAndGet());
        assertThat(counter.get()).isEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // Retry on retryable errors
    // -------------------------------------------------------------------------

    @Test
    void execute_retriesOnRetryableError_thenSucceeds() throws InterruptedException {
        var retry = RetryPolicy.builder()
                .maxAttempts(3)
                .initialBackoffMs(0) // no sleep in tests
                .maxBackoffMs(0)
                .build();

        var counter = new AtomicInteger(0);
        var result = retry.execute(() -> {
            if (counter.incrementAndGet() < 3) {
                throw retryableError();
            }
            return "success";
        });

        assertThat(result).isEqualTo("success");
        assertThat(counter.get()).isEqualTo(3); // 2 failures + 1 success
    }

    @Test
    void execute_exhaustsAllAttempts_throwsLastException() {
        var retry = RetryPolicy.builder()
                .maxAttempts(3)
                .initialBackoffMs(0)
                .maxBackoffMs(0)
                .build();

        var counter = new AtomicInteger(0);
        assertThatThrownBy(() -> retry.execute(() -> {
            counter.incrementAndGet();
            throw retryableError();
        }))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("Deadlock");

        assertThat(counter.get()).isEqualTo(3); // All 3 attempts exhausted
    }

    // -------------------------------------------------------------------------
    // Fatal (non-retryable) errors propagate immediately
    // -------------------------------------------------------------------------

    @Test
    void execute_fatalError_propagatesImmediatelyWithoutRetry() {
        var retry = RetryPolicy.builder()
                .maxAttempts(5)
                .initialBackoffMs(0)
                .maxBackoffMs(0)
                .build();

        var counter = new AtomicInteger(0);
        assertThatThrownBy(() -> retry.execute(() -> {
            counter.incrementAndGet();
            throw fatalError();
        }))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("UNKNOWN_CONSUMER");

        assertThat(counter.get()).isEqualTo(1); // Only 1 attempt — no retries
    }

    // -------------------------------------------------------------------------
    // Builder validation
    // -------------------------------------------------------------------------

    @Test
    void builder_rejectsZeroMaxAttempts() {
        assertThatThrownBy(() -> RetryPolicy.builder().maxAttempts(0).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxAttempts");
    }

    @Test
    void builder_rejectsNegativeMaxAttempts() {
        assertThatThrownBy(() -> RetryPolicy.builder().maxAttempts(-1).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void builder_rejectsNegativeInitialBackoff() {
        assertThatThrownBy(() -> RetryPolicy.builder().initialBackoffMs(-1).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void builder_rejectsNegativeMaxBackoff() {
        assertThatThrownBy(() -> RetryPolicy.builder().maxBackoffMs(-1).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    // -------------------------------------------------------------------------
    // Defaults factory
    // -------------------------------------------------------------------------

    @Test
    void defaults_usesDefaultValues() throws InterruptedException {
        var retry = RetryPolicy.defaults();
        var result = retry.execute(() -> 42);
        assertThat(result).isEqualTo(42);
    }

    // -------------------------------------------------------------------------
    // Single attempt (maxAttempts = 1)
    // -------------------------------------------------------------------------

    @Test
    void singleAttempt_throwsImmediatelyOnFailure() {
        var retry = RetryPolicy.builder()
                .maxAttempts(1)
                .build();

        assertThatThrownBy(() -> retry.execute(() -> { throw retryableError(); }))
                .isInstanceOf(DataAccessException.class);
    }
}
