package boxy.core.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link InputValidator}.
 */
class InputValidatorTest {

    // -------------------------------------------------------------------------
    // requireValidPath
    // -------------------------------------------------------------------------

    @Test
    void requireValidPath_validPaths() {
        assertThatCode(() -> InputValidator.requireValidPath("/"))
                .doesNotThrowAnyException();
        assertThatCode(() -> InputValidator.requireValidPath("tenant-a/payments"))
                .doesNotThrowAnyException();
        assertThatCode(() -> InputValidator.requireValidPath("a"))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void requireValidPath_rejectsNullOrBlank(String path) {
        assertThatThrownBy(() -> InputValidator.requireValidPath(path))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path");
    }

    @Test
    void requireValidPath_rejectsExceedingMaxLength() {
        String longPath = "a".repeat(InputValidator.MAX_PATH_LENGTH + 1);
        assertThatThrownBy(() -> InputValidator.requireValidPath(longPath))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path");
    }

    @Test
    void requireValidPath_rejectsControlChars() {
        assertThatThrownBy(() -> InputValidator.requireValidPath("test\u0000path"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("control characters");

        assertThatThrownBy(() -> InputValidator.requireValidPath("test\u001Fpath"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("control characters");

        assertThatThrownBy(() -> InputValidator.requireValidPath("test\u007Fpath"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("control characters");
    }

    @Test
    void requireValidPath_acceptsMaxLengthPath() {
        String maxPath = "a".repeat(InputValidator.MAX_PATH_LENGTH);
        assertThatCode(() -> InputValidator.requireValidPath(maxPath))
                .doesNotThrowAnyException();
    }

    // -------------------------------------------------------------------------
    // requireValidName
    // -------------------------------------------------------------------------

    @Test
    void requireValidName_validNames() {
        assertThatCode(() -> InputValidator.requireValidName("events", "topic"))
                .doesNotThrowAnyException();
        assertThatCode(() -> InputValidator.requireValidName("my-subscription", "subscription"))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void requireValidName_rejectsNullOrBlank(String name) {
        assertThatThrownBy(() -> InputValidator.requireValidName(name, "topic"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("topic");
    }

    @Test
    void requireValidName_rejectsExceedingMaxLength() {
        String longName = "x".repeat(InputValidator.MAX_NAME_LENGTH + 1);
        assertThatThrownBy(() -> InputValidator.requireValidName(longName, "topic"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("topic");
    }

    @Test
    void requireValidName_rejectsControlChars() {
        assertThatThrownBy(() -> InputValidator.requireValidName("bad\nname", "topic"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("control characters");
    }

    @Test
    void requireValidName_acceptsMaxLengthName() {
        String maxName = "a".repeat(InputValidator.MAX_NAME_LENGTH);
        assertThatCode(() -> InputValidator.requireValidName(maxName, "name"))
                .doesNotThrowAnyException();
    }

    // -------------------------------------------------------------------------
    // requireValidPartitionCount
    // -------------------------------------------------------------------------

    @Test
    void requireValidPartitionCount_validRange() {
        assertThatCode(() -> InputValidator.requireValidPartitionCount(1))
                .doesNotThrowAnyException();
        assertThatCode(() -> InputValidator.requireValidPartitionCount(512))
                .doesNotThrowAnyException();
        assertThatCode(() -> InputValidator.requireValidPartitionCount(1024))
                .doesNotThrowAnyException();
    }

    @Test
    void requireValidPartitionCount_rejectsZero() {
        assertThatThrownBy(() -> InputValidator.requireValidPartitionCount(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("partitions");
    }

    @Test
    void requireValidPartitionCount_rejectsNegative() {
        assertThatThrownBy(() -> InputValidator.requireValidPartitionCount(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("partitions");
    }

    @Test
    void requireValidPartitionCount_rejectsExceedingMax() {
        assertThatThrownBy(() -> InputValidator.requireValidPartitionCount(1025))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("partitions");
    }

    // -------------------------------------------------------------------------
    // Unicode handling
    // -------------------------------------------------------------------------

    @Test
    void requireValidPath_acceptsUnicodeCharacters() {
        // Unicode characters above U+007F should be accepted
        assertThatCode(() -> InputValidator.requireValidPath("テスト/パス"))
                .doesNotThrowAnyException();
        assertThatCode(() -> InputValidator.requireValidPath("café/données"))
                .doesNotThrowAnyException();
    }

    @Test
    void requireValidName_acceptsEmoji() {
        assertThatCode(() -> InputValidator.requireValidName("events-🚀", "topic"))
                .doesNotThrowAnyException();
    }

    // -------------------------------------------------------------------------
    // Byte-length enforcement (not character-length)
    // -------------------------------------------------------------------------

    @Test
    void requireValidName_enforcesBytes_notChars() {
        // "á" is 2 bytes in UTF-8; repeat to fill exactly MAX_NAME_LENGTH bytes
        // 250 copies of "á" (2 bytes each) = 500 bytes = MAX_NAME_LENGTH → should pass
        String atLimit = "á".repeat(InputValidator.MAX_NAME_LENGTH / 2);
        assertThat(atLimit.getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                .isEqualTo(InputValidator.MAX_NAME_LENGTH);
        assertThatCode(() -> InputValidator.requireValidName(atLimit, "name"))
                .doesNotThrowAnyException();

        // One more "á" → 502 bytes → should fail
        String overLimit = atLimit + "á";
        assertThatThrownBy(() -> InputValidator.requireValidName(overLimit, "name"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bytes");
    }

    @Test
    void requireValidPath_enforcesBytes_notChars() {
        // 4-byte emoji: 🚀 is 4 bytes in UTF-8
        // MAX_PATH_LENGTH / 4 = 1000 emojis = 4000 bytes → should pass
        String atLimit = "🚀".repeat(InputValidator.MAX_PATH_LENGTH / 4);
        assertThat(atLimit.getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                .isEqualTo(InputValidator.MAX_PATH_LENGTH);
        assertThatCode(() -> InputValidator.requireValidPath(atLimit))
                .doesNotThrowAnyException();

        // One more emoji → 4004 bytes → should fail
        String overLimit = atLimit + "🚀";
        assertThatThrownBy(() -> InputValidator.requireValidPath(overLimit))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bytes");
    }
}
