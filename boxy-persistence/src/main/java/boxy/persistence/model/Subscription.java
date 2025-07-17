package boxy.persistence.model;

public record Subscription(
        long id,
        long consumerGroupId,
        long topicId) {
}
