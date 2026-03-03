package boxy.core.worker;

import java.time.Duration;

/**
 * Configuration for the Worker async event consumer.
 *
 * Build with the builder pattern:
 * <pre>
 * WorkerConfig config = WorkerConfig.builder()
 *     .subscriptionName("my-consumer-group")
 *     .path("/")
 *     .topic("events")
 *     .maxBatchSize(100)
 *     .pollTimeoutMs(5000)
 *     .maxConcurrentPollers(4)
 *     .build();
 * </pre>
 *
 * @since 1.0
 */
public class WorkerConfig {
    private final String subscriptionName;
    private final String path;
    private final String topic;
    private final int maxBatchSize;
    private final Duration pollTimeout;
    private final int maxConcurrentPollers;
    private final boolean autoCommit;
    private final Duration shutdownTimeout;

    private WorkerConfig(Builder builder) {
        this.subscriptionName = builder.subscriptionName;
        this.path = builder.path;
        this.topic = builder.topic;
        this.maxBatchSize = builder.maxBatchSize;
        this.pollTimeout = builder.pollTimeout;
        this.maxConcurrentPollers = builder.maxConcurrentPollers;
        this.autoCommit = builder.autoCommit;
        this.shutdownTimeout = builder.shutdownTimeout;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String subscriptionName() {
        return subscriptionName;
    }

    public String path() {
        return path;
    }

    public String topic() {
        return topic;
    }

    public int maxBatchSize() {
        return maxBatchSize;
    }

    public Duration pollTimeout() {
        return pollTimeout;
    }

    public int maxConcurrentPollers() {
        return maxConcurrentPollers;
    }

    public boolean autoCommit() {
        return autoCommit;
    }

    public Duration shutdownTimeout() {
        return shutdownTimeout;
    }

    /**
     * Builder for WorkerConfig.
     */
    public static class Builder {
        private String subscriptionName;
        private String path = "/";
        private String topic;
        private int maxBatchSize = 50;
        private Duration pollTimeout = Duration.ofSeconds(5);
        private int maxConcurrentPollers = 4;
        private boolean autoCommit = true;
        private Duration shutdownTimeout = Duration.ofSeconds(10);

        public Builder subscriptionName(String name) {
            this.subscriptionName = name;
            return this;
        }

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder topic(String topic) {
            this.topic = topic;
            return this;
        }

        public Builder maxBatchSize(int size) {
            this.maxBatchSize = size;
            return this;
        }

        public Builder pollTimeout(Duration timeout) {
            this.pollTimeout = timeout;
            return this;
        }

        public Builder maxConcurrentPollers(int count) {
            this.maxConcurrentPollers = count;
            return this;
        }

        public Builder autoCommit(boolean enabled) {
            this.autoCommit = enabled;
            return this;
        }

        public Builder shutdownTimeout(Duration timeout) {
            this.shutdownTimeout = timeout;
            return this;
        }

        public WorkerConfig build() {
            if (subscriptionName == null || subscriptionName.isBlank()) {
                throw new IllegalArgumentException("subscriptionName is required");
            }
            if (topic == null || topic.isBlank()) {
                throw new IllegalArgumentException("topic is required");
            }
            return new WorkerConfig(this);
        }
    }
}
