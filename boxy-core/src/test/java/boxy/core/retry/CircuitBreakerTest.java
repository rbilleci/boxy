package boxy.core.retry;

import boxy.core.DataAccessException;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link CircuitBreaker}.
 */
class CircuitBreakerTest {

    // -------------------------------------------------------------------------
    // Helper: create retryable and non-retryable DataAccessExceptions
    // -------------------------------------------------------------------------

    private static DataAccessException retryableError() {
        // MySQL 1213 = deadlock → isRetryable() == true
        return new DataAccessException(new SQLException("Deadlock", "40001", 1213));
    }

    private static DataAccessException fatalError() {
        // SQLSTATE 45000 = SIGNAL → isRetryable() == false
        return new DataAccessException(new SQLException("UNKNOWN_CONSUMER", "45000", 0));
    }

    // -------------------------------------------------------------------------
    // CLOSED state
    // -------------------------------------------------------------------------

    @Test
    void initialState_isClosed() {
        var cb = CircuitBreaker.defaults("test");
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(cb.getFailureCount()).isZero();
    }

    @Test
    void closedState_passesThrough() {
        var cb = CircuitBreaker.defaults("test");
        var result = cb.execute(() -> "hello");
        assertThat(result).isEqualTo("hello");
    }

    @Test
    void closedState_nonRetryableError_doesNotIncrementFailures() {
        var cb = CircuitBreaker.of("test", 2, 10_000);

        assertThatThrownBy(() -> cb.execute(() -> { throw fatalError(); }))
                .isInstanceOf(DataAccessException.class);

        // Non-retryable errors do NOT count toward the threshold
        assertThat(cb.getFailureCount()).isZero();
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void closedState_retryableErrors_underThreshold_staysClosed() {
        var cb = CircuitBreaker.of("test", 3, 10_000);

        // 2 failures (threshold is 3)
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> cb.execute(() -> { throw retryableError(); }))
                    .isInstanceOf(DataAccessException.class);
        }

        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(cb.getFailureCount()).isEqualTo(2);
    }

    @Test
    void closedState_successResetsFailureCount() {
        var cb = CircuitBreaker.of("test", 5, 10_000);

        // 3 retryable failures
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> cb.execute(() -> { throw retryableError(); }))
                    .isInstanceOf(DataAccessException.class);
        }
        assertThat(cb.getFailureCount()).isEqualTo(3);

        // 1 success resets counter
        cb.execute(() -> "ok");
        assertThat(cb.getFailureCount()).isZero();
    }

    // -------------------------------------------------------------------------
    // CLOSED → OPEN transition
    // -------------------------------------------------------------------------

    @Test
    void closedToOpen_afterThresholdRetryableFailures() {
        var cb = CircuitBreaker.of("test", 3, 60_000);

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> cb.execute(() -> { throw retryableError(); }))
                    .isInstanceOf(DataAccessException.class);
        }

        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void openState_rejectsCallsWithOpenException() {
        var cb = CircuitBreaker.of("test", 1, 60_000);

        // Trip the breaker
        assertThatThrownBy(() -> cb.execute(() -> { throw retryableError(); }))
                .isInstanceOf(DataAccessException.class);

        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // Subsequent calls are rejected without invoking the action
        assertThatThrownBy(() -> cb.execute(() -> "should not run"))
                .isInstanceOf(CircuitBreaker.OpenException.class)
                .hasMessageContaining("OPEN");
    }

    // -------------------------------------------------------------------------
    // OPEN → HALF_OPEN transition
    // -------------------------------------------------------------------------

    @Test
    void openToHalfOpen_afterResetTimeout() {
        // Use 0ms reset timeout so it transitions immediately
        var cb = CircuitBreaker.of("test", 1, 0);

        // Trip to OPEN
        assertThatThrownBy(() -> cb.execute(() -> { throw retryableError(); }))
                .isInstanceOf(DataAccessException.class);

        // With 0ms timeout, evaluateState should transition to HALF_OPEN
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
    }

    // -------------------------------------------------------------------------
    // HALF_OPEN → CLOSED (on success)
    // -------------------------------------------------------------------------

    @Test
    void halfOpenToClosed_onSuccess() {
        var cb = CircuitBreaker.of("test", 1, 0);

        // Trip to OPEN, then immediately to HALF_OPEN
        assertThatThrownBy(() -> cb.execute(() -> { throw retryableError(); }))
                .isInstanceOf(DataAccessException.class);

        // Probe should succeed → CLOSED
        var result = cb.execute(() -> "recovered");
        assertThat(result).isEqualTo("recovered");
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(cb.getFailureCount()).isZero();
    }

    // -------------------------------------------------------------------------
    // HALF_OPEN → OPEN (on failure)
    // -------------------------------------------------------------------------

    @Test
    void halfOpenToOpen_onFailure() {
        var cb = CircuitBreaker.of("test", 1, 0);

        // Trip to OPEN → HALF_OPEN
        assertThatThrownBy(() -> cb.execute(() -> { throw retryableError(); }))
                .isInstanceOf(DataAccessException.class);

        // Probe fails → back to OPEN
        assertThatThrownBy(() -> cb.execute(() -> { throw retryableError(); }))
                .isInstanceOf(DataAccessException.class);

        // Since resetTimeout is 0, it immediately goes to HALF_OPEN again,
        // but the failure should have been registered
        // Check that we're not CLOSED
        assertThat(cb.getState()).isNotEqualTo(CircuitBreaker.State.CLOSED);
    }

    // -------------------------------------------------------------------------
    // Runnable overload
    // -------------------------------------------------------------------------

    @Test
    void executeRunnable_works() {
        var cb = CircuitBreaker.defaults("test");
        var holder = new boolean[]{false};

        cb.execute(() -> holder[0] = true);
        assertThat(holder[0]).isTrue();
    }

    // -------------------------------------------------------------------------
    // Factory methods
    // -------------------------------------------------------------------------

    @Test
    void defaults_usesDefaultValues() {
        var cb = CircuitBreaker.defaults("my-cb");
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(cb.getFailureCount()).isZero();
    }
}
