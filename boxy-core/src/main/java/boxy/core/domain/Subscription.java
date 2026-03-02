package boxy.core.domain;

import java.time.Instant;

/**
 * Represents a subscription that consumers can join to read events.
 *
 * <p>Subscriptions are logical groupings of topics. Consumers register to a subscription
 * and specify which topics to subscribe to. Multiple subscriptions can exist for the same
 * topic, allowing independent consumer groups.
 *
 * @param id Unique subscription identifier (auto-generated)
 * @param name Subscription name (human-readable identifier)
 * @param lastModifiedAt When the subscription was last updated
 */
public record Subscription(
        long id,
        String name,
        Instant lastModifiedAt) {
}
