package boxy.core.model;

import java.time.Instant;

public record Lease(
        long subscriptionOffsetId,
        long workerId,
        long version,
        Instant acquiredAt,
        String status,
        Instant releaseStartedAt) {
    
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_RELEASING = "RELEASING";
}
