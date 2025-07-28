package boxy.core.model;

import java.time.Instant;

public record ConsumerGroup(
        long id,
        String tenant,
        String name,
        double heartbeatIntervalDefault,
        double heartbeatIntervalMin,
        double heartbeatIntervalMax,
        double heartbeatDeadlineMultiplier,
        double heartbeatQpsTarget,
        int releaseDeadline,
        int totalWeight,
        int activePartitions,
        int activeWorkers,
        Instant lastUpdated) {
}
