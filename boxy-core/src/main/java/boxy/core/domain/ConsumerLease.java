package boxy.core.domain;

import java.time.Instant;

/**
 * Represents a time-limited lease on a cursor held by a consumer.
 *
 * <p>Leases prevent duplicate event delivery by ensuring only one consumer can read
 * from a partition at a time. Leases auto-expire and are released explicitly on commit.
 * The last_read_position tracks the furthest sequence the consumer has read (not necessarily committed).
 *
 * @param id Lease record ID (cursor_id, used as primary key)
 * @param consumerId Consumer holding the lease
 * @param cursorId The cursor being leased
 * @param lockedUntil Lease expiry timestamp (null = not locked/released)
 * @param lastReadPosition Furthest sequence read by this consumer on this cursor
 */
public record ConsumerLease(
        long id,
        String consumerId,
        long cursorId,
        Instant lockedUntil,
        long lastReadPosition) {
}
