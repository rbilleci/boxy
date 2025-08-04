package boxy.core.domain;

import java.time.Instant;

public record Subscription(
        long id,
        long consumerGroupId,
        long topicId,
        double heartbeatInterval,
        int activePartitions,
        int activeConsumers,
        double activeConsumersWeight,
        Instant createdAt,
        Instant lastModifiedAt) {
}
