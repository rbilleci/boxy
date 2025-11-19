package boxy.core.domain;

import java.time.Instant;
import java.util.List;

public record Consumer(
        String id,
        long subscriptionId,
        double weight,
        Instant heartbeatDetectedAt,
        double heartbeatInterval,
        Instant heartbeatDeadline,
        List<Long> topicIds) {
}
