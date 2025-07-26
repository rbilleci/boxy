package boxy.core.model;

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
        int heartbeatInterval,
        int leaseTtl,
        List<SubscriptionOffset> addedLeases,
        List<SubscriptionOffset> removedLeases) {
}