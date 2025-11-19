package boxy.core.domain;

public record ConsumerRegistration(
        String consumerId,
        long subscriptionId,
        long topicId) {
}
