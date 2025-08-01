package boxy.core.domain;

import java.time.Instant;

public record SubscriptionTopic(
        long id,
        long subscriptionId,
        long topicId,
        Instant createdAt) {
}
