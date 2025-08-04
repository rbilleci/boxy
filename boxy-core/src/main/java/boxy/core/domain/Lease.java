package boxy.core.domain;

import java.time.Instant;

public record Lease(
        long cursorId,
        String consumerId,
        Instant acquiredAt,
        Instant releasedAt,
        Instant releaseDeadline,
        LeaseState state) {
    
    public enum LeaseState {
        ACTIVE,
        RELEASING
    }
}
