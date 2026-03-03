package boxy.core.worker;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link WorkerConfig} builder validation.
 */
class WorkerConfigTest {

    private WorkerConfig.Builder validBuilder() {
        return WorkerConfig.builder()
                .subscriptionName("test-group")
                .path("/tenant")
                .topic("events");
    }

    @Test
    void validConfig_builds() {
        var config = validBuilder().build();
        assertThat(config.subscriptionName()).isEqualTo("test-group");
        assertThat(config.path()).isEqualTo("/tenant");
        assertThat(config.topic()).isEqualTo("events");
        assertThat(config.maxBatchSize()).isEqualTo(50);
        assertThat(config.maxConcurrentPollers()).isEqualTo(4);
        assertThat(config.autoCommit()).isTrue();
    }

    @Test
    void rejectsNullSubscriptionName() {
        assertThatThrownBy(() -> validBuilder().subscriptionName(null).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("subscriptionName");
    }

    @Test
    void rejectsBlankSubscriptionName() {
        assertThatThrownBy(() -> validBuilder().subscriptionName("  ").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("subscriptionName");
    }

    @Test
    void rejectsNullTopic() {
        assertThatThrownBy(() -> validBuilder().topic(null).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("topic");
    }

    @Test
    void rejectsBlankPath() {
        assertThatThrownBy(() -> validBuilder().path("  ").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path");
    }

    @Test
    void rejectsZeroMaxBatchSize() {
        assertThatThrownBy(() -> validBuilder().maxBatchSize(0).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxBatchSize");
    }

    @Test
    void rejectsNegativeMaxBatchSize() {
        assertThatThrownBy(() -> validBuilder().maxBatchSize(-1).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxBatchSize");
    }

    @Test
    void rejectsExcessiveMaxBatchSize() {
        assertThatThrownBy(() -> validBuilder().maxBatchSize(10_001).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxBatchSize");
    }

    @Test
    void acceptsMaxBatchSizeBoundary() {
        assertThatCode(() -> validBuilder().maxBatchSize(1).build())
                .doesNotThrowAnyException();
        assertThatCode(() -> validBuilder().maxBatchSize(10_000).build())
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsZeroMaxConcurrentPollers() {
        assertThatThrownBy(() -> validBuilder().maxConcurrentPollers(0).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxConcurrentPollers");
    }

    @Test
    void rejectsNegativeMaxConcurrentPollers() {
        assertThatThrownBy(() -> validBuilder().maxConcurrentPollers(-1).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxConcurrentPollers");
    }

    @Test
    void rejectsZeroPollTimeout() {
        assertThatThrownBy(() -> validBuilder().pollTimeout(Duration.ZERO).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pollTimeout");
    }

    @Test
    void rejectsNegativePollTimeout() {
        assertThatThrownBy(() -> validBuilder().pollTimeout(Duration.ofMillis(-1)).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pollTimeout");
    }

    @Test
    void rejectsNegativeShutdownTimeout() {
        assertThatThrownBy(() -> validBuilder().shutdownTimeout(Duration.ofMillis(-1)).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("shutdownTimeout");
    }

    @Test
    void acceptsZeroShutdownTimeout() {
        // Zero shutdown = immediate forced shutdown, which is valid
        assertThatCode(() -> validBuilder().shutdownTimeout(Duration.ZERO).build())
                .doesNotThrowAnyException();
    }

    @Test
    void customValues_arePreserved() {
        var config = validBuilder()
                .maxBatchSize(200)
                .maxConcurrentPollers(8)
                .pollTimeout(Duration.ofSeconds(10))
                .autoCommit(false)
                .shutdownTimeout(Duration.ofSeconds(30))
                .build();

        assertThat(config.maxBatchSize()).isEqualTo(200);
        assertThat(config.maxConcurrentPollers()).isEqualTo(8);
        assertThat(config.pollTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(config.autoCommit()).isFalse();
        assertThat(config.shutdownTimeout()).isEqualTo(Duration.ofSeconds(30));
    }
}
