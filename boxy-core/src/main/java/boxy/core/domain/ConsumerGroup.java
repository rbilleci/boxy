package boxy.core.domain;

import java.time.Instant;

public record ConsumerGroup(
        long id,
        String name,
        double heartbeatDeadlineMultiplier,
        double heartbeatIntervalBaseline,
        double heartbeatInterval,
        double heartbeatIntervalLimit,
        double heartbeatTargetQPS,
        int metricsRefreshInterval,
        int leaseReleasePeriod,
        int activePartitions,
        int activeConsumers,
        int activeConsumersLimit,
        double activeConsumersWeight,
        Instant lastModifiedAt) {
}
