package boxy.core.model;

import java.util.List;

/**
 * Result of a worker check-in operation, containing statistics and lease changes.
 */
public record WorkerCheckInResult(
        // Statistics for the worker
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
        
        // Lease changes
        List<SubscriptionOffset> addedLeases,
        List<SubscriptionOffset> removedLeases
) {
    /**
     * Calculates the next check-in time based on the heartbeat interval and a randomization factor.
     * This helps distribute check-ins evenly and avoid thundering herd problems.
     * 
     * @return The number of seconds until the next check-in
     */
    public int calculateNextCheckInTime() {
        // Add some randomization (±20%) to avoid synchronization
        double randomFactor = 0.8 + (Math.random() * 0.4); // 0.8 to 1.2
        return (int) (heartbeatInterval * randomFactor);
    }
    
    /**
     * Checks if the worker is within its fair share of leases.
     * 
     * @return true if the worker has an appropriate number of leases
     */
    public boolean isWithinFairShare() {
        return currentLeases >= minLeases && currentLeases <= maxLeases;
    }
}