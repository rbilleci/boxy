package boxy.persistence.model;

import java.time.Instant;

public record Lease(
        long subscriptionOffsetId,
        String owner,
        long version,
        Instant acquiredAt,
        Instant updatedAt,
        Instant expiresAt) {
}
