package boxy.core.model;

import java.time.Instant;

public record Lease(
        long subscriptionOffsetId,
        long workerId,
        long version,
        Instant acquiredAt,
        Instant releasedAt,
        LeaseState state) {
    
    public enum LeaseState {
        ACTIVE,
        RELEASING
    }
}
