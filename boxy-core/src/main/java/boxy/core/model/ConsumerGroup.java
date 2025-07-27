package boxy.core.model;

import java.time.Instant;

public record ConsumerGroup(
        long id,
        String tenant,
        String name,
        double heartbeatIntervalDefault,
        double heartbeatDeadlineMultiplier,
        int releaseDeadline,
        double heartbeatQpsTarget,
        double heartbeatIntervalMin,
        double heartbeatIntervalMax,
        int totalWeight,
        int activePartitions,
        int activeWorkers,
        Instant lastUpdated) {
}
