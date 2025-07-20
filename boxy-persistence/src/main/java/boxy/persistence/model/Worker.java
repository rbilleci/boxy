package boxy.persistence.model;

import java.time.Instant;

public record Worker(
        long id,
        String nodeId,
        long consumerGroupId,
        int weight,
        Instant lastHeartbeat) {
}
