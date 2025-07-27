package boxy.core.model;

import java.time.LocalDateTime;

public record ConsumerGroup(
        long id,
        String tenant,
        String name,
        double heartbeatIntervalDefault,
        double heartbeatDeadlineMultiplier,
        int activeWorkersCount,
        int totalWeight,
        int activePartitionsCount,
        LocalDateTime lastUpdated) {
}
