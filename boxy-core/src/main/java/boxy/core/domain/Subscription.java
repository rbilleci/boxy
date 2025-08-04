package boxy.core.domain;

import java.time.Instant;

public record Subscription(
        long id,
        long consumerGroupId,
        long topicId,
        Instant createdAt) {
}
