package boxy.core.domain;

import java.time.Instant;

public record Lease(
        long subscriptionOffsetId,
        String workerId,
        long version,
        Instant acquiredAt,
        Instant releasedAt,
        LeaseState state) {
    
    public enum LeaseState {
        ACTIVE,
        RELEASING
    }
}
