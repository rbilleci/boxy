package boxy.core.util;

/**
 * Input validation utilities for Boxy namespace, topic, and subscription names.
 *
 * <p>All public methods throw {@link IllegalArgumentException} with a descriptive
 * message on validation failure, allowing callers to distinguish user-input errors
 * from database failures ({@link boxy.core.DataAccessException}).
 *
 * <p>Validation is enforced at the Java layer so that invalid input never reaches
 * the stored procedures or the database, regardless of the SQL driver or character
 * set configuration.
 *
 * <p>Items #107, #108, #109.
 */
public final class InputValidator {

    /** Maximum length for namespace and topic name segments (bytes, not characters). */
    public static final int MAX_NAME_LENGTH = 500;

    /** Maximum length for a fully-qualified namespace path. */
    public static final int MAX_PATH_LENGTH = 4000;

    /** Maximum number of partitions allowed per topic. */
    public static final int MAX_PARTITIONS = 1024;

    /** Minimum number of partitions required per topic. */
    public static final int MIN_PARTITIONS = 1;

    private InputValidator() {}

    /**
     * Validates a namespace path (e.g. {@code "tenant-a/payments"}).
     *
     * <p>Checks performed (item #107, #109):
     * <ul>
     *   <li>Not null or blank</li>
     *   <li>Length ≤ {@link #MAX_PATH_LENGTH}</li>
     *   <li>No ASCII control characters (U+0000–U+001F, U+007F)</li>
     *   <li>No null bytes (U+0000)</li>
     * </ul>
     *
     * @param path the namespace path to validate
     * @throws IllegalArgumentException if the path is invalid
     */
    public static void requireValidPath(final String path) {
        requireNonBlank(path, "path");
        requireMaxLength(path, MAX_PATH_LENGTH, "path");
        requireNoControlChars(path, "path");
    }

    /**
     * Validates a topic or subscription name.
     *
     * <p>Checks performed (item #107, #109):
     * <ul>
     *   <li>Not null or blank</li>
     *   <li>Length ≤ {@link #MAX_NAME_LENGTH}</li>
     *   <li>No ASCII control characters (U+0000–U+001F, U+007F)</li>
     * </ul>
     *
     * @param name  the name to validate
     * @param field human-readable field name for error messages
     * @throws IllegalArgumentException if the name is invalid
     */
    public static void requireValidName(final String name, final String field) {
        requireNonBlank(name, field);
        requireMaxLength(name, MAX_NAME_LENGTH, field);
        requireNoControlChars(name, field);
    }

    /**
     * Validates a topic partition count.
     *
     * <p>Checks performed (item #108):
     * <ul>
     *   <li>≥ {@link #MIN_PARTITIONS}</li>
     *   <li>≤ {@link #MAX_PARTITIONS}</li>
     * </ul>
     *
     * @param partitions the partition count to validate
     * @throws IllegalArgumentException if the count is out of range
     */
    public static void requireValidPartitionCount(final int partitions) {
        if (partitions < MIN_PARTITIONS || partitions > MAX_PARTITIONS) {
            throw new IllegalArgumentException(
                    "partitions must be between " + MIN_PARTITIONS + " and " + MAX_PARTITIONS
                    + " (got " + partitions + ")");
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static void requireNonBlank(final String value, final String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be null or blank");
        }
    }

    private static void requireMaxLength(final String value, final int max, final String field) {
        if (value.length() > max) {
            throw new IllegalArgumentException(
                    field + " must not exceed " + max + " characters (got " + value.length() + ")");
        }
    }

    /**
     * Rejects strings containing ASCII control characters (U+0000–U+001F) or
     * the delete character (U+007F) which can cause issues in SQL or JSON contexts.
     */
    private static void requireNoControlChars(final String value, final String field) {
        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);
            if (c < 0x20 || c == 0x7F) {
                throw new IllegalArgumentException(
                        field + " must not contain control characters (found U+"
                        + String.format("%04X", (int) c) + " at index " + i + ")");
            }
        }
    }
}
