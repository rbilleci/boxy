package boxy.core.domain;

import java.time.Instant;

public record Subscription(
        long id,
        String tenant,
        String name,
        double heartbeatDeadlineMultiplier,
        double heartbeatIntervalBaseline,
        double heartbeatInterval,
        double heartbeatIntervalLimit,
        double heartbeatTargetQPS,
        int metricsRefreshInterval,
        int leaseReleasePeriod,
        int activePartitions,
        int activeWorkers,
        int activeWorkersLimit,
        double activeWorkersWeight,
        Instant lastUpdated) {
}
