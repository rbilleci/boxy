package boxy.core.model;

import java.time.Instant;
import java.util.List;

public record CheckInResult(
        long workerId,
        int activeWorkers,
        double activeWorkersWeight,
        int activePartitions,
        double workerWeight,
        double idealShare,
        int currentLeases,
        int minLeases,
        int maxLeases,
        double heartbeatInterval,
        Instant heartbeatDeadline,
        List<SubscriptionOffset> activeLeases) {
}