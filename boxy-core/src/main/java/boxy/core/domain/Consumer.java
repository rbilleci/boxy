package boxy.core.domain;

import java.time.Instant;
import java.util.List;

/**
 * Represents an active event consumer registered to a subscription.
 *
 * <p>Each consumer instance maintains its own heartbeat deadline and subscribed topic list.
 * Consumers are automatically garbage-collected if their heartbeat expires.
 *
 * @param id Unique consumer identifier (e.g., "order-processor-1")
 * @param subscriptionId The subscription this consumer is registered to
 * @param heartbeatDetectedAt Timestamp of the last heartbeat signal (poll or commit)
 * @param heartbeatInterval Heartbeat interval in seconds (used to compute deadline)
 * @param heartbeatDeadline Absolute timestamp after which this consumer is considered dead
 * @param topicIds JSON array of topic IDs this consumer is subscribed to
 */
public record Consumer(
        String id,
        long subscriptionId,
        Instant heartbeatDetectedAt,
        double heartbeatInterval,
        Instant heartbeatDeadline,
        List<Long> topicIds) {
}
