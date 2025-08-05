package boxy.core.domain;

public record ConsumerSubscription(
        String consumerId,
        long subscriptionId,
        long topicId) {
}
