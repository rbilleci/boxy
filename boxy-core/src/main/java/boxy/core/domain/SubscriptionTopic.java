package boxy.core.domain;

import java.time.Instant;

public record SubscriptionTopic(
        long id,
        long subscriptionId,
        long topicId,
        double heartbeatInterval,
        int activePartitions,
        int activeConsumers,
        Instant createdAt,
        Instant lastModifiedAt) {
}
