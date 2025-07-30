package boxy.core.domain;

public record SubscriptionOffset(
        long id,
        long subscriptionId,
        long partitionId,
        int randomKey,
        long committedOffset) {
}
