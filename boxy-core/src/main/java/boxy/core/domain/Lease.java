package boxy.core.domain;

import java.time.Instant;

public record Lease(
        long cursorId,
        String consumerId,
        Instant lockedUntil) {
}
