package boxy.core.model;

import java.time.Instant;

public record Worker(
        long id,
        String nodeId,
        long consumerGroupId,
        double weight,
        Instant lastHeartbeat,
        double heartbeatInterval,
        Instant heartbeatDeadline) {
}
