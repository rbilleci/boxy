package boxy.core.model;

public record SubscriptionOffset(
        long id,
        long subscriptionId,
        long partitionId,
        long committedOffset,
        long highWatermark) {
}
