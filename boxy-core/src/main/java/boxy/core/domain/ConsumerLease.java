package boxy.core.domain;

import java.time.Instant;

public record ConsumerLease(
        long id,
        String consumerId,
        long cursorId,
        Instant lockedUntil,
        long lastReadPosition) {
}
