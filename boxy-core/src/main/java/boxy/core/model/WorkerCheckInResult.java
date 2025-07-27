package boxy.core.model;

import java.time.Instant;
import java.util.List;

public record WorkerCheckInResult(
        long workerId,
        int activeWorkers,
        int activePartitions,
        int totalWeight,
        int workerWeight,
        double idealShare,
        int currentLeases,
        int minLeases,
        int maxLeases,
        double heartbeatInterval,
        Instant heartbeatDeadline,
        List<SubscriptionOffset> activeLeases) {
}