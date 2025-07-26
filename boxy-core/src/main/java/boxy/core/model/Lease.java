package boxy.core.model;

import java.time.Instant;

public record Lease(
        long subscriptionOffsetId,
        long workerId,
        long version,
        Instant acquiredAt,
        Instant updatedAt,
        Instant expiresAt,
        LeaseState state) {
    
    public enum LeaseState {
        ACTIVE,
        RELEASING
    }
}
