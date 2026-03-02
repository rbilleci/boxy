package boxy.core.util;

/**
 * Minimal JSON serialisation utilities for Boxy's internal use.
 *
 * <p>These utilities avoid adding a JSON library dependency to the core module
 * while providing correct, RFC 8259-compliant serialisation for the specific
 * data types used in Boxy's stored-procedure call interfaces.
 *
 * <p>Items #105 and #106: Replace manual JSON construction with correct
 * serialisers that handle all string edge cases (control characters, surrogate
 * pairs, null bytes) and prevent injection of arbitrary JSON.
 */
public final class JsonUtils {

    private JsonUtils() {}

    /**
     * Serialises a string value to a JSON string literal, correctly escaping
     * all characters required by RFC 8259 §7.
     *
     * <p>Escaped sequences:
     * <ul>
     *   <li>{@code "} → {@code \"}</li>
     *   <li>{@code \} → {@code \\}</li>
     *   <li>backspace (U+0008) → {@code \b}</li>
     *   <li>form feed  (U+000C) → {@code \f}</li>
     *   <li>newline    (U+000A) → {@code \n}</li>
     *   <li>carriage return (U+000D) → {@code \r}</li>
     *   <li>tab        (U+0009) → {@code \t}</li>
     *   <li>other control characters (U+0000–U+001F, U+007F) → {@code \uXXXX}</li>
     * </ul>
     *
     * @param value the string to serialise; {@code null} is serialised as {@code null}
     * @return a JSON string literal (including surrounding double-quotes), or
     *         {@code "null"} if {@code value} is {@code null}
     */
    public static String jsonString(final String value) {
        if (value == null) return "null";
        final var sb = new StringBuilder(value.length() + 4).append('"');
        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default   -> {
                    if (c < 0x20 || c == 0x7F) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    /**
     * Serialises a list of strings to a JSON array literal.
     *
     * <p>Each element is serialised using {@link #jsonString(String)}.
     * An empty list is serialised as {@code []}.
     *
     * @param values the list of strings; must not be {@code null}
     * @return a JSON array string, e.g. {@code ["a","b","c"]}
     */
    public static String jsonArray(final java.util.List<String> values) {
        final var sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(jsonString(values.get(i)));
        }
        return sb.append(']').toString();
    }

    /**
     * Serialises a {@link java.util.Map}{@code <Long, Long>} to a JSON object literal.
     *
     * <p>Keys are serialised as JSON string literals (quoted longs).
     * Values are serialised as JSON numbers (unquoted longs).
     *
     * @param map the map to serialise; must not be {@code null}
     * @return a JSON object string, e.g. {@code {"1":100,"2":200}}
     */
    public static String jsonObject(final java.util.Map<Long, Long> map) {
        final var sb = new StringBuilder("{");
        boolean first = true;
        for (final var entry : map.entrySet()) {
            if (!first) sb.append(',');
            sb.append('"').append(entry.getKey()).append("\":").append(entry.getValue());
            first = false;
        }
        return sb.append('}').toString();
    }
}
