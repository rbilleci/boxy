package boxy.core.domain;

public record SubscriptionTopic(
        long id,
        long subscriptionId,
        long topicId) {
}
