package boxy.core.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link JsonUtils}.
 */
class JsonUtilsTest {

    // -------------------------------------------------------------------------
    // jsonString
    // -------------------------------------------------------------------------

    @Test
    void jsonString_simpleValue() {
        assertThat(JsonUtils.jsonString("hello")).isEqualTo("\"hello\"");
    }

    @Test
    void jsonString_emptyString() {
        assertThat(JsonUtils.jsonString("")).isEqualTo("\"\"");
    }

    @Test
    void jsonString_nullReturnsLiteral() {
        assertThat(JsonUtils.jsonString(null)).isEqualTo("null");
    }

    @Test
    void jsonString_escapesDoubleQuotes() {
        assertThat(JsonUtils.jsonString("say \"hi\"")).isEqualTo("\"say \\\"hi\\\"\"");
    }

    @Test
    void jsonString_escapesBackslash() {
        assertThat(JsonUtils.jsonString("path\\to")).isEqualTo("\"path\\\\to\"");
    }

    @Test
    void jsonString_escapesNewline() {
        assertThat(JsonUtils.jsonString("line1\nline2")).isEqualTo("\"line1\\nline2\"");
    }

    @Test
    void jsonString_escapesCarriageReturn() {
        assertThat(JsonUtils.jsonString("line1\rline2")).isEqualTo("\"line1\\rline2\"");
    }

    @Test
    void jsonString_escapesTab() {
        assertThat(JsonUtils.jsonString("col1\tcol2")).isEqualTo("\"col1\\tcol2\"");
    }

    @Test
    void jsonString_escapesBackspace() {
        assertThat(JsonUtils.jsonString("back\bspace")).isEqualTo("\"back\\bspace\"");
    }

    @Test
    void jsonString_escapesFormFeed() {
        assertThat(JsonUtils.jsonString("form\ffeed")).isEqualTo("\"form\\ffeed\"");
    }

    @Test
    void jsonString_escapesNullByte() {
        assertThat(JsonUtils.jsonString("null\u0000byte")).isEqualTo("\"null\\u0000byte\"");
    }

    @Test
    void jsonString_escapesControlChars() {
        // U+001F (unit separator) should be \u001f
        assertThat(JsonUtils.jsonString("test\u001Fchar")).isEqualTo("\"test\\u001fchar\"");
    }

    @Test
    void jsonString_escapesDeleteChar() {
        // U+007F (DEL) should be \u007f
        assertThat(JsonUtils.jsonString("test\u007Fchar")).isEqualTo("\"test\\u007fchar\"");
    }

    @Test
    void jsonString_preservesUnicode() {
        // Characters above U+007F should pass through unescaped
        assertThat(JsonUtils.jsonString("café")).isEqualTo("\"café\"");
        assertThat(JsonUtils.jsonString("日本語")).isEqualTo("\"日本語\"");
    }

    // -------------------------------------------------------------------------
    // jsonArray
    // -------------------------------------------------------------------------

    @Test
    void jsonArray_emptyList() {
        assertThat(JsonUtils.jsonArray(List.of())).isEqualTo("[]");
    }

    @Test
    void jsonArray_singleElement() {
        assertThat(JsonUtils.jsonArray(List.of("hello"))).isEqualTo("[\"hello\"]");
    }

    @Test
    void jsonArray_multipleElements() {
        assertThat(JsonUtils.jsonArray(List.of("a", "b", "c"))).isEqualTo("[\"a\",\"b\",\"c\"]");
    }

    @Test
    void jsonArray_elementsWithSpecialChars() {
        assertThat(JsonUtils.jsonArray(List.of("say \"hi\"", "path\\to")))
                .isEqualTo("[\"say \\\"hi\\\"\",\"path\\\\to\"]");
    }

    // -------------------------------------------------------------------------
    // jsonObject
    // -------------------------------------------------------------------------

    @Test
    void jsonObject_emptyMap() {
        assertThat(JsonUtils.jsonObject(Map.of())).isEqualTo("{}");
    }

    @Test
    void jsonObject_singleEntry() {
        var map = new LinkedHashMap<Long, Long>();
        map.put(1L, 100L);
        assertThat(JsonUtils.jsonObject(map)).isEqualTo("{\"1\":100}");
    }

    @Test
    void jsonObject_multipleEntries() {
        var map = new LinkedHashMap<Long, Long>();
        map.put(1L, 100L);
        map.put(2L, 200L);
        assertThat(JsonUtils.jsonObject(map)).isEqualTo("{\"1\":100,\"2\":200}");
    }

    @Test
    void jsonObject_largeValues() {
        var map = new LinkedHashMap<Long, Long>();
        map.put(Long.MAX_VALUE, Long.MIN_VALUE);
        String result = JsonUtils.jsonObject(map);
        assertThat(result).contains(String.valueOf(Long.MAX_VALUE));
        assertThat(result).contains(String.valueOf(Long.MIN_VALUE));
    }
}
