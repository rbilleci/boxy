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
        int activePartitions,
        int activeWorkers,
        double activeWorkersWeight,
        Instant lastUpdated) {
}
