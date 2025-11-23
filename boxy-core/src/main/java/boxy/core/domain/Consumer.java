package boxy.core.domain;

import java.time.Instant;
import java.util.List;

public record Consumer(
        String id,
        long subscriptionId,
        Instant heartbeatDetectedAt,
        double heartbeatInterval,
        Instant heartbeatDeadline,
        List<Long> topicIds) {
}
