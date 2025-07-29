package boxy.core.domain;

public record Subscription(
        long id,
        long consumerGroupId,
        long topicId) {
}
