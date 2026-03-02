package boxy.core;

import java.sql.SQLException;

/**
 * Unchecked wrapper around {@link SQLException} thrown by all Boxy repository operations.
 *
 * <p>Preserves the original SQL state and vendor-specific error code so callers can
 * distinguish expected error conditions (e.g. {@code UNKNOWN_CONSUMER} signalled by a stored
 * procedure) from unexpected infrastructure failures.
 *
 * <p>Stored procedures use {@code SIGNAL SQLSTATE '45000'} with a {@code MESSAGE_TEXT} of the
 * form {@code ERROR_CODE}. Callers can check {@link #getSqlState()} and
 * {@link #getVendorCode()} or call {@link #hasErrorCode(String)} for structured error handling.
 *
 * <p>Item #110: The {@link #isRetryable()} method classifies known transient error conditions
 * (lock timeouts, deadlocks, connection loss) so retry strategies can skip fatal errors
 * such as constraint violations and procedure signal codes.
 *
 * <p>Item #113: {@link #getOperation()} and {@link #getParameterCount()} carry the SQL
 * operation and parameter count from the call site, enriching exception logs.
 */
public class DataAccessException extends RuntimeException {

    // -------------------------------------------------------------------------
    // MySQL transient error codes (retryable)
    // -------------------------------------------------------------------------

    /** MySQL: Lock wait timeout exceeded (InnoDB row-level lock timeout). */
    private static final int MYSQL_ER_LOCK_WAIT_TIMEOUT = 1205;

    /** MySQL: Deadlock found when trying to get lock. */
    private static final int MYSQL_ER_LOCK_DEADLOCK = 1213;

    /** MySQL: Query execution was interrupted. */
    private static final int MYSQL_ER_QUERY_INTERRUPTED = 1317;

    /** MySQL: Lost connection to server during query. */
    private static final int MYSQL_ER_LOST_CONNECTION = 2013;

    /** MySQL: Can't connect to MySQL server. */
    private static final int MYSQL_ER_CANT_CONNECT = 2003;

    /** SQLSTATE 40001: Serialization failure (deadlock) — standard across databases. */
    private static final String SQLSTATE_SERIALIZATION_FAILURE = "40001";

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /** The five-character SQLSTATE value from the originating {@link SQLException}. */
    private final String sqlState;

    /** The database-specific vendor error code (e.g. MySQL error number). */
    private final int vendorCode;

    /**
     * The SQL operation string that was executing when the error occurred.
     * May be {@code null} if constructed without operation context.
     */
    private final String operation;

    /**
     * Number of bind parameters passed to the failing statement.
     * {@code -1} if not known.
     */
    private final int parameterCount;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Constructs a {@code DataAccessException} that wraps the given {@link SQLException}.
     *
     * @param cause the originating SQL exception; must not be {@code null}
     */
    public DataAccessException(final SQLException cause) {
        this(cause, null, -1);
    }

    /**
     * Constructs a {@code DataAccessException} with full operation context (item #113).
     *
     * @param cause          the originating SQL exception; must not be {@code null}
     * @param operation      the SQL string or stored-procedure call that failed (may be {@code null})
     * @param parameterCount the number of bind parameters; {@code -1} if unknown
     */
    public DataAccessException(final SQLException cause, final String operation, final int parameterCount) {
        super(buildMessage(cause, operation), cause);
        this.sqlState       = cause.getSQLState();
        this.vendorCode     = cause.getErrorCode();
        this.operation      = operation;
        this.parameterCount = parameterCount;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the SQLSTATE string from the wrapped exception, or {@code null} if not available.
     *
     * @return the SQLSTATE value (e.g. {@code "45000"} for a stored-procedure SIGNAL)
     */
    public String getSqlState() {
        return sqlState;
    }

    /**
     * Returns the database-vendor-specific error code from the wrapped exception, or {@code 0}
     * if not available.
     *
     * @return the vendor error code (e.g. MySQL error number 1205 for a lock timeout)
     */
    public int getVendorCode() {
        return vendorCode;
    }

    /**
     * Returns the SQL operation string from the call site, or {@code null} if not available.
     *
     * @return the SQL string or stored-procedure call that was executing, or {@code null}
     */
    public String getOperation() {
        return operation;
    }

    /**
     * Returns the number of bind parameters that were passed to the failing statement.
     *
     * @return the parameter count, or {@code -1} if unknown
     */
    public int getParameterCount() {
        return parameterCount;
    }

    // -------------------------------------------------------------------------
    // Error classification (item #110)
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if this exception represents a <em>transient</em> database error
     * that may succeed on a subsequent attempt (item #110).
     *
     * <p>Retryable conditions:
     * <ul>
     *   <li>MySQL 1205 — Lock wait timeout (row-level lock not acquired within timeout)</li>
     *   <li>MySQL 1213 — Deadlock (InnoDB chose this transaction as deadlock victim)</li>
     *   <li>MySQL 1317 — Query interrupted</li>
     *   <li>MySQL 2003 — Cannot connect to server</li>
     *   <li>MySQL 2013 — Lost connection to server</li>
     *   <li>SQLSTATE 40001 — Serialization failure (standard deadlock state)</li>
     * </ul>
     *
     * <p>Fatal (non-retryable) conditions include constraint violations (SQLSTATE 23000),
     * stored-procedure SIGNAL errors (SQLSTATE 45000, e.g. UNKNOWN_CONSUMER, PAYLOAD_TOO_LARGE),
     * and syntax errors.
     *
     * @return {@code true} if a retry is potentially useful; {@code false} for fatal errors
     */
    public boolean isRetryable() {
        if (SQLSTATE_SERIALIZATION_FAILURE.equals(sqlState)) {
            return true;
        }
        return switch (vendorCode) {
            case MYSQL_ER_LOCK_WAIT_TIMEOUT,
                 MYSQL_ER_LOCK_DEADLOCK,
                 MYSQL_ER_QUERY_INTERRUPTED,
                 MYSQL_ER_LOST_CONNECTION,
                 MYSQL_ER_CANT_CONNECT -> true;
            default -> false;
        };
    }

    // -------------------------------------------------------------------------
    // Existing helpers
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if the exception message contains the given Boxy error code token.
     *
     * <p>Stored procedures signal errors using {@code MESSAGE_TEXT} of the form
     * {@code "ERROR_CODE"}. This helper lets callers check for a specific error without
     * parsing the full message.
     *
     * <pre>{@code
     * try {
     *     consumerRepo.register(id, subscription, topics);
     * } catch (DataAccessException e) {
     *     if (e.hasErrorCode("UNKNOWN_CONSUMER")) {
     *         // handle gracefully
     *     } else {
     *         throw e;
     *     }
     * }
     * }</pre>
     *
     * @param code the Boxy error token to check for (e.g. {@code "UNKNOWN_CONSUMER"})
     * @return {@code true} if the message equals or starts with {@code code}
     */
    public boolean hasErrorCode(final String code) {
        final var msg = getMessage();
        return msg != null && (msg.equals(code) || msg.startsWith(code + ":"));
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static String buildMessage(final SQLException cause, final String operation) {
        if (operation == null) return cause.getMessage();
        return "[" + operation + "] " + cause.getMessage();
    }
}
