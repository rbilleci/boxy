package boxy.core.it;

import boxy.core.dao.*;
import boxy.core.model.*;
import boxy.core.worker.WorkStealer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public class WorkStealerIT extends BaseIT {

    private WorkerDao workerDao;
    private LeaseDao leaseDao;
    private SubscriptionOffsetDao subscriptionOffsetDao;
    private EventDao eventDao;
    private TestData data;

    // Configuration parameters for WorkStealer
    private static final int HEARTBEAT_INTERVAL_SECONDS = 1;
    private static final int HEARTBEAT_TIMEOUT_SECONDS = 5;
    private static final long LEASE_EXPIRY_MILLIS = TimeUnit.SECONDS.toMillis(10);
    private static final double SLACK = 0.1;
    private static final int BATCH_SIZE = 10;

    @BeforeEach
    void setup() {
        workerDao = new WorkerDao(dataSource);
        leaseDao = new LeaseDao(dataSource);
        subscriptionOffsetDao = new SubscriptionOffsetDao(dataSource);
        eventDao = new EventDao(dataSource);
        data = TestData.seed(dataSource);
    }

    @Test
    void heartbeat_updatesLastHeartbeatTimestamp() {
        // Create a WorkStealer instance
        WorkStealer workStealer = new WorkStealer(
                workerDao,
                leaseDao,
                subscriptionOffsetDao,
                "node-1",
                data.consumerGroups().get(0).id(),
                1,
                HEARTBEAT_INTERVAL_SECONDS,
                HEARTBEAT_TIMEOUT_SECONDS,
                LEASE_EXPIRY_MILLIS,
                SLACK,
                BATCH_SIZE
        );

        // Register the worker
        long workerId = workStealer.register(1);

        // Get the initial worker
        Worker initialWorker = workerDao.find(workerId).orElseThrow();
        
        // Wait a bit to ensure the timestamp will be different
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Update the heartbeat
        boolean updated = workStealer.heartbeat();
        assertThat(updated).isTrue();

        // Get the updated worker
        Worker updatedWorker = workerDao.find(workerId).orElseThrow();

        // Verify that the heartbeat timestamp was updated
        assertThat(updatedWorker.lastHeartbeat()).isAfter(initialWorker.lastHeartbeat());
    }

    @Test
    void cleanupExpiredWorkers_removesExpiredWorkersAndTheirLeases() throws InterruptedException {
        // Create a worker with a short heartbeat timeout
        String nodeId = UUID.randomUUID().toString();
        long workerId = workerDao.register(nodeId, data.consumerGroups().get(0).id(), 1);
        
        // Acquire a lease for this worker
        leaseDao.acquire(data.subscriptionOffset().id(), workerId, LEASE_EXPIRY_MILLIS);
        
        // Verify the lease exists
        assertThat(leaseDao.find(data.subscriptionOffset().id())).isPresent();
        
        // Wait for the heartbeat to expire
        Thread.sleep(HEARTBEAT_TIMEOUT_SECONDS * 1000 + 1000); // Add 1 second buffer
        
        // Create a WorkStealer instance
        WorkStealer workStealer = new WorkStealer(
                workerDao,
                leaseDao,
                subscriptionOffsetDao,
                "node-2",
                data.consumerGroups().get(0).id(),
                1,
                HEARTBEAT_INTERVAL_SECONDS,
                HEARTBEAT_TIMEOUT_SECONDS,
                LEASE_EXPIRY_MILLIS,
                SLACK,
                BATCH_SIZE
        );
        
        // Clean up expired workers
        int cleaned = workStealer.cleanupExpiredWorkers();
        
        // Verify that the worker was cleaned up
        assertThat(cleaned).isGreaterThan(0);
        assertThat(workerDao.find(workerId)).isEmpty();
        
        // Verify that the lease was released
        assertThat(leaseDao.find(data.subscriptionOffset().id())).isEmpty();
    }

    @Test
    void stealWork_distributesLeasesAccordingToFairShare() {
        // Create multiple workers with different weights
        WorkStealer workStealer1 = new WorkStealer(
                workerDao,
                leaseDao,
                subscriptionOffsetDao,
                "node-1",
                data.consumerGroups().get(0).id(),
                2, // Weight 2
                HEARTBEAT_INTERVAL_SECONDS,
                HEARTBEAT_TIMEOUT_SECONDS,
                LEASE_EXPIRY_MILLIS,
                SLACK,
                BATCH_SIZE
        );
        
        WorkStealer workStealer2 = new WorkStealer(
                workerDao,
                leaseDao,
                subscriptionOffsetDao,
                "node-2",
                data.consumerGroups().get(0).id(),
                1, // Weight 1
                HEARTBEAT_INTERVAL_SECONDS,
                HEARTBEAT_TIMEOUT_SECONDS,
                LEASE_EXPIRY_MILLIS,
                SLACK,
                BATCH_SIZE
        );
        
        // Register the workers
        long workerId1 = workStealer1.register(2);
        long workerId2 = workStealer2.register(1);
        
        // Create some active partitions by publishing events
        // We'll publish events to the topic directly
        String tenant = TestData.TENANT_1;
        String topic = TestData.TOPIC_A;
        
        // Publish 10 events with different keys to ensure they go to different partitions
        for (int i = 0; i < 10; i++) {
            String key = "key-" + i;
            eventDao.publish(tenant, topic, key, "{\"message\":\"test\"}");
        }
        
        // Run the work-stealing algorithm for both workers
        workStealer1.stealWork();
        workStealer2.stealWork();
        
        // Count the leases held by each worker
        int leaseCount1 = leaseDao.countByWorkerId(workerId1);
        int leaseCount2 = leaseDao.countByWorkerId(workerId2);
        
        // Verify that the leases are distributed according to the fair share
        // Worker 1 should have approximately 2/3 of the leases (weight 2 out of total weight 3)
        // Worker 2 should have approximately 1/3 of the leases (weight 1 out of total weight 3)
        assertThat(leaseCount1).isGreaterThanOrEqualTo(6); // ~6-7 leases (2/3 of 10)
        assertThat(leaseCount2).isGreaterThanOrEqualTo(3); // ~3-4 leases (1/3 of 10)
        assertThat(leaseCount1 + leaseCount2).isLessThanOrEqualTo(10); // Total should not exceed active partitions
    }
}