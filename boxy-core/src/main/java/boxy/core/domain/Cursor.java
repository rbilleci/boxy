package boxy.core.domain;

public record Cursor(
        long id,
        long subscriptionId,
        long subscriptionTopicId,
        long partitionId,
        int randomKey,
        long position) {
}
