package boxy.core.model;

public record Subscription(
        long id,
        long consumerGroupId,
        long topicId) {
}
