package boxy.core.domain;

public record Cursor(
        long id,
        long subscriptionTopicId,
        long subscriptionId,
        long partitionId,
        int randomKey,
        long position) {
}
