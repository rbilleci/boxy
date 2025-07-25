package boxy.core.worker;

import boxy.core.dao.LeaseDao;
import boxy.core.dao.SubscriptionOffsetDao;
import boxy.core.dao.WorkerDao;
import boxy.core.model.Lease;
import boxy.core.model.SubscriptionOffset;
import boxy.core.model.Worker;
import boxy.core.util.WorkStealingUtil;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implements the decentralized randomized work-stealing algorithm for distributing
 * partition leases among workers in a consumer group.
 */
public class WorkStealer {
    private static final Logger LOGGER = Logger.getLogger(WorkStealer.class.getName());

    private final WorkerDao workerDao;
    private final LeaseDao leaseDao;
    private final SubscriptionOffsetDao subscriptionOffsetDao;
    
    // Configuration parameters
    private final String nodeId;
    private final long consumerGroupId;
    private final int heartbeatIntervalSeconds;
    private final int heartbeatTimeoutSeconds;
    private final long leaseExpiryMillis;
    private final double slack;
    private final int batchSize;
    
    private Worker worker;

    /**
     * Creates a new WorkStealer instance.
     *
     * @param workerDao The WorkerDao instance
     * @param leaseDao The LeaseDao instance
     * @param subscriptionOffsetDao The SubscriptionOffsetDao instance
     * @param nodeId The node ID of this worker
     * @param consumerGroupId The consumer group ID
     * @param weight The weight of this worker
     * @param heartbeatIntervalSeconds The interval between heartbeats in seconds
     * @param heartbeatTimeoutSeconds The timeout for heartbeats in seconds
     * @param leaseExpiryMillis The lease expiry time in milliseconds
     * @param slack The slack parameter to avoid thrashing
     * @param batchSize The batch size for grabbing and releasing leases
     */
    public WorkStealer(
            WorkerDao workerDao,
            LeaseDao leaseDao,
            SubscriptionOffsetDao subscriptionOffsetDao,
            String nodeId,
            long consumerGroupId,
            int weight,
            int heartbeatIntervalSeconds,
            int heartbeatTimeoutSeconds,
            long leaseExpiryMillis,
            double slack,
            int batchSize) {
        this.workerDao = workerDao;
        this.leaseDao = leaseDao;
        this.subscriptionOffsetDao = subscriptionOffsetDao;
        this.nodeId = nodeId;
        this.consumerGroupId = consumerGroupId;
        this.heartbeatIntervalSeconds = heartbeatIntervalSeconds;
        this.heartbeatTimeoutSeconds = heartbeatTimeoutSeconds;
        this.leaseExpiryMillis = leaseExpiryMillis;
        this.slack = slack;
        this.batchSize = batchSize;
    }

    /**
     * Registers this worker with the system.
     *
     * @param weight The weight of this worker
     * @return The worker ID
     */
    public long register(int weight) {
        long workerId = workerDao.register(nodeId, consumerGroupId, weight);
        Optional<Worker> workerOpt = workerDao.find(workerId);
        if (workerOpt.isPresent()) {
            this.worker = workerOpt.get();
            LOGGER.info("Registered worker: " + worker);
            return workerId;
        } else {
            throw new IllegalStateException("Failed to register worker");
        }
    }

    /**
     * Deregisters this worker from the system.
     */
    public void deregister() {
        if (worker != null) {
            workerDao.deregister(worker.id());
            LOGGER.info("Deregistered worker: " + worker);
            worker = null;
        }
    }

    /**
     * Updates the heartbeat for this worker.
     *
     * @return true if the heartbeat was updated successfully, false otherwise
     */
    public boolean heartbeat() {
        if (worker == null) {
            return false;
        }
        
        boolean updated = workerDao.heartbeat(nodeId, consumerGroupId);
        if (updated) {
            LOGGER.fine("Updated heartbeat for worker: " + worker.id());
        } else {
            LOGGER.warning("Failed to update heartbeat for worker: " + worker.id());
        }
        return updated;
    }

    /**
     * Cleans up expired workers.
     *
     * @return The number of workers cleaned up
     */
    public int cleanupExpiredWorkers() {
        int cleaned = workerDao.cleanupExpiredWorkers(heartbeatTimeoutSeconds);
        if (cleaned > 0) {
            LOGGER.info("Cleaned up " + cleaned + " expired workers");
        }
        return cleaned;
    }

    /**
     * Executes one iteration of the work-stealing algorithm.
     * This method should be called periodically.
     */
    public void stealWork() {
        if (worker == null) {
            LOGGER.warning("Worker not registered, cannot steal work");
            return;
        }

        try {
            // Clean up expired workers
            cleanupExpiredWorkers();
            
            // Update heartbeat
            if (!heartbeat()) {
                LOGGER.warning("Failed to update heartbeat, re-registering worker");
                register(worker.weight());
                return;
            }
            
            // Get active workers in the consumer group
            List<Worker> activeWorkers = workerDao.findActiveByConsumerGroup(consumerGroupId, heartbeatTimeoutSeconds);
            
            // Count active partitions
            int activePartitions = subscriptionOffsetDao.countActivePartitions(consumerGroupId);
            
            // Calculate fair share
            double fairShare = WorkStealingUtil.calculateFairShare(worker, activeWorkers, activePartitions);
            
            // Count current leases
            int currentLeaseCount = leaseDao.countByWorkerId(worker.id());
            
            LOGGER.info(String.format(
                "Worker %d: fairShare=%.2f, currentLeases=%d, activeWorkers=%d, activePartitions=%d",
                worker.id(), fairShare, currentLeaseCount, activeWorkers.size(), activePartitions));
            
            // Check if we need to release leases
            if (WorkStealingUtil.shouldReleaseLeases(currentLeaseCount, fairShare, slack)) {
                int toRelease = WorkStealingUtil.calculateLeasesToRelease(currentLeaseCount, fairShare);
                LOGGER.info("Releasing " + toRelease + " leases");
                
                int released = leaseDao.releaseCount(worker.id(), toRelease);
                LOGGER.info("Released " + released + " leases");
            }
            // Check if we need to grab more leases
            else if (WorkStealingUtil.shouldGrabMoreLeases(currentLeaseCount, fairShare, slack)) {
                int toGrab = WorkStealingUtil.calculateLeasesToGrab(currentLeaseCount, fairShare);
                LOGGER.info("Grabbing " + toGrab + " leases");
                
                // Get available leases with random pivot
                List<SubscriptionOffset> availableLeases = subscriptionOffsetDao.leasesAvailableRandomPivot(
                        consumerGroupId, Math.min(toGrab, batchSize));
                
                if (!availableLeases.isEmpty()) {
                    int acquired = leaseDao.acquireBatch(availableLeases, worker.id(), leaseExpiryMillis);
                    LOGGER.info("Acquired " + acquired + " leases");
                } else {
                    LOGGER.fine("No available leases to grab");
                }
            }
            // We're within the fair band, do nothing
            else {
                LOGGER.fine("Within fair band, no action needed");
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error in work-stealing algorithm", e);
        }
    }
}