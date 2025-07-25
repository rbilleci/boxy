package boxy.core.util;

import boxy.core.model.Worker;

import java.util.List;

/**
 * Utility class for the work-stealing algorithm.
 */
public final class WorkStealingUtil {

    private WorkStealingUtil() {
        // Utility class, no instances
    }

    /**
     * Calculates the fair share of partitions for a worker based on its weight and the total weight of all active workers.
     *
     * @param worker The worker to calculate the fair share for
     * @param activeWorkers List of all active workers in the consumer group
     * @param activePartitions Total number of active partitions (where high_watermark > committed_offset)
     * @return The fair share of partitions for the worker
     */
    public static double calculateFairShare(Worker worker, List<Worker> activeWorkers, int activePartitions) {
        if (activeWorkers.isEmpty() || activePartitions <= 0) {
            return 0.0;
        }

        int totalWeight = activeWorkers.stream()
                .mapToInt(Worker::weight)
                .sum();

        if (totalWeight <= 0) {
            return 0.0;
        }

        return (double) worker.weight() / totalWeight * activePartitions;
    }

    /**
     * Determines if a worker should grab more leases based on its current lease count, fair share, and slack.
     *
     * @param currentLeaseCount The number of leases currently held by the worker
     * @param fairShare The calculated fair share of partitions for the worker
     * @param slack A small value to avoid thrashing (e.g., 0.1)
     * @return true if the worker should grab more leases, false otherwise
     */
    public static boolean shouldGrabMoreLeases(int currentLeaseCount, double fairShare, double slack) {
        return currentLeaseCount < Math.ceil(fairShare) - slack;
    }

    /**
     * Determines if a worker should release some leases based on its current lease count, fair share, and slack.
     *
     * @param currentLeaseCount The number of leases currently held by the worker
     * @param fairShare The calculated fair share of partitions for the worker
     * @param slack A small value to avoid thrashing (e.g., 0.1)
     * @return true if the worker should release some leases, false otherwise
     */
    public static boolean shouldReleaseLeases(int currentLeaseCount, double fairShare, double slack) {
        return currentLeaseCount > Math.floor(fairShare) + slack;
    }

    /**
     * Calculates the number of leases to release.
     *
     * @param currentLeaseCount The number of leases currently held by the worker
     * @param fairShare The calculated fair share of partitions for the worker
     * @return The number of leases to release
     */
    public static int calculateLeasesToRelease(int currentLeaseCount, double fairShare) {
        return currentLeaseCount - (int) Math.floor(fairShare);
    }

    /**
     * Calculates the number of leases to grab.
     *
     * @param currentLeaseCount The number of leases currently held by the worker
     * @param fairShare The calculated fair share of partitions for the worker
     * @return The number of leases to grab
     */
    public static int calculateLeasesToGrab(int currentLeaseCount, double fairShare) {
        return (int) Math.ceil(fairShare) - currentLeaseCount;
    }
}