package boxy.core.domain;

public record Cursor(
        long id,
        long subscriptionId,
        long partitionId,
        int randomKey,
        long committedOffset) {
}
