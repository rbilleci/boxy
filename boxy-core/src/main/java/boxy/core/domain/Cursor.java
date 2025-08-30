package boxy.core.domain;

import java.time.Instant;

public record Cursor(
        long id,
        long subscriptionId,
        long subscriptionTopicId,
        long topicId,
        long partitionId,
        int randomKey,
        long position,
        String lockedBy,
        Instant lockedUntil) {
}