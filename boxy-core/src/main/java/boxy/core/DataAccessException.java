package boxy.core;

import java.sql.SQLException;

/**
 * Unchecked wrapper around {@link SQLException} thrown by all Boxy repository operations.
 *
 * <p>Preserves the original SQL state and vendor-specific error code so callers can
 * distinguish expected error conditions (e.g. {@code UNKNOWN_CONSUMER} signalled by a stored
 * procedure) from unexpected infrastructure failures.
 *
 * <p>Stored procedures use {@code SIGNAL SQLSTATE 'HY000'} with a {@code MESSAGE_TEXT} of the
 * form {@code ERROR_CODE: detail}. Callers can check {@link #getSqlState()} and
 * {@link #getVendorCode()} or call {@link #hasErrorCode(String)} for structured error handling.
 */
public class DataAccessException extends RuntimeException {

    /** The five-character SQLSTATE value from the originating {@link SQLException}. */
    private final String sqlState;

    /** The database-specific vendor error code (e.g. MySQL error number). */
    private final int vendorCode;

    /**
     * Constructs a {@code DataAccessException} that wraps the given {@link SQLException}.
     *
     * @param cause the originating SQL exception; must not be {@code null}
     */
    public DataAccessException(final SQLException cause) {
        super(cause.getMessage(), cause);
        this.sqlState = cause.getSQLState();
        this.vendorCode = cause.getErrorCode();
    }

    /**
     * Returns the SQLSTATE string from the wrapped exception, or {@code null} if not available.
     *
     * @return the SQLSTATE value (e.g. {@code "HY000"} for a stored-procedure SIGNAL)
     */
    public String getSqlState() {
        return sqlState;
    }

    /**
     * Returns the database-vendor-specific error code from the wrapped exception, or {@code 0}
     * if not available.
     *
     * @return the vendor error code (e.g. MySQL error number 1644 for an unhandled user SIGNAL)
     */
    public int getVendorCode() {
        return vendorCode;
    }

    /**
     * Returns {@code true} if the exception message contains the given Boxy error code token.
     *
     * <p>Stored procedures signal errors using {@code MESSAGE_TEXT} of the form
     * {@code "ERROR_CODE: human-readable detail"} (e.g. {@code "UNKNOWN_CONSUMER: id=abc"}).
     * This helper lets callers check for a specific error without parsing the full message.
     *
     * <pre>{@code
     * try {
     *     consumerRepo.register(id, subscription, topics);
     * } catch (DataAccessException e) {
     *     if (e.hasErrorCode("DUPLICATE_CONSUMER")) {
     *         // handle gracefully
     *     } else {
     *         throw e;
     *     }
     * }
     * }</pre>
     *
     * @param code the Boxy error token to check for (e.g. {@code "UNKNOWN_CONSUMER"})
     * @return {@code true} if the message starts with {@code code + ":"}
     */
    public boolean hasErrorCode(final String code) {
        final var msg = getMessage();
        return msg != null && msg.startsWith(code + ":");
    }
}
