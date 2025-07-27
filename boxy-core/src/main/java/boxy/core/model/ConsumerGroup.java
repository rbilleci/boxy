package boxy.core.model;

import java.time.Instant;

public record ConsumerGroup(
        long id,
        String tenant,
        String name,
        double heartbeatIntervalDefault,
        double heartbeatDeadlineMultiplier,
        int activeWorkersCount,
        int totalWeight,
        int activePartitionsCount,
        Instant lastUpdated) {
}
