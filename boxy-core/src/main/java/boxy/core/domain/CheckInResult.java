package boxy.core.domain;

import java.time.Instant;
import java.util.List;

public record CheckInResult(
        String workerId,
        int activeWorkers,
        double activeWorkersWeight,
        int activeWorkersLimit,
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