package boxy.core.model;

import java.util.List;

public record WorkerCheckInResult(
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

    /**
     * Calculates the next check-in time based on the heartbeat interval and a randomization factor.
     * This helps distribute check-ins evenly and avoid thundering herd problems.
     *
     * @return The number of seconds until the next check-in
     */
    public int calculateNextCheckInTime() {
        // Add some randomization (±20%) to avoid synchronization
        final var randomFactor = 0.8 + (Math.random() * 0.4); // 0.8 to 1.2
        return (int) (heartbeatInterval * randomFactor);
    }

}