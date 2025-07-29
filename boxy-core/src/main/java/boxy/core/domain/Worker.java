package boxy.core.domain;

import java.time.Instant;

public record Worker(
        String id,
        long consumerGroupId,
        double weight,
        Instant heartbeatDetectedAt,
        double heartbeatInterval,
        Instant heartbeatDeadline) {
}
