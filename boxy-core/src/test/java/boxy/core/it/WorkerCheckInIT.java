package boxy.core.it;

import boxy.core.dao.*;
import boxy.core.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@Testcontainers
public class WorkerCheckInIT extends BaseIT {

    private WorkerDao workerDao;
    private LeaseDao leaseDao;
    private SubscriptionOffsetDao subscriptionOffsetDao;
    private EventDao eventDao;
    private TestData data;

    @BeforeEach
    void setup() {
        workerDao = new WorkerDao(dataSource);
        leaseDao = new LeaseDao(dataSource);
        subscriptionOffsetDao = new SubscriptionOffsetDao(dataSource);
        eventDao = new EventDao(dataSource);
        data = TestData.seed(dataSource);

        // Publish some events to create active partitions
        for (int i = 0; i < 10; i++) {
            // Calculate partition ID (topic_id << 16) + partition_number
            long partitionId = (data.topic().id() << 16) + 0;
            eventDao.publishAdvanced(partitionId, "{\"message\":\"test" + i + "\"}");
        }
    }

    @Test
    void checkIn_singleWorker_acquiresAllLeases() {
        // Given a single worker
        final var worker = data.worker1();

        // When the worker checks in
        final var result = workerDao.checkIn(worker.nodeId(), worker.consumerGroupId(), worker.weight(), 5);

        // Then the worker should acquire all active leases
        assertThat(result.activeWorkers()).isEqualTo(1);
        assertThat(result.activePartitions()).isGreaterThan(0);
        assertThat(result.totalWeight()).isEqualTo(worker.weight());
        assertThat(result.workerWeight()).isEqualTo(worker.weight());
        assertThat(result.idealShare()).isEqualTo(result.activePartitions());
        assertThat(result.currentLeases()).isEqualTo(0); // Initially 0 before acquisition
        assertThat(result.minLeases()).isLessThanOrEqualTo(result.activePartitions());
        assertThat(result.maxLeases()).isGreaterThanOrEqualTo(result.activePartitions());
        assertThat(result.heartbeatInterval()).isGreaterThan(0);
        assertThat(result.heartbeatDeadline()).isNotNull();

        // Verify added leases
        assertThat(result.addedLeases()).isNotEmpty();
        assertThat(result.addedLeases().size()).isGreaterThanOrEqualTo(result.minLeases());

        // Verify no removed leases
        assertThat(result.removedLeases()).isEmpty();

        // Verify leases in database
        for (final var offset : result.addedLeases()) {
            assertThat(leaseDao.find(offset.id()))
                    .isPresent()
                    .get()
                    .extracting(Lease::workerId)
                    .isEqualTo(worker.id());
        }
    }

    @Test
    void checkIn_multipleWorkers_distributesLeasesFairly() {
        // Given two workers with equal weight
        final var worker1 = data.worker1();
        final var worker2 = data.worker2();

        // Make sure they're in the same consumer group
        final var worker2Id = workerDao.checkIn(
                UUID.randomUUID().toString(), worker1.consumerGroupId(), worker1.weight(), 5).workerId();
        final var updatedWorker2 = workerDao.find(worker2Id).orElseThrow();

        // When worker1 checks in first
        final var result1 = workerDao.checkIn(
                worker1.nodeId(),
                worker1.consumerGroupId(),
                worker1.weight(),
                5);

        // Then worker1 should acquire all active leases
        assertThat(result1.activeWorkers()).isEqualTo(1);
        assertThat(result1.addedLeases()).isNotEmpty();

        // When worker2 checks in
        final var result2 = workerDao.checkIn(
                updatedWorker2.nodeId(),
                updatedWorker2.consumerGroupId(),
                updatedWorker2.weight(),
                5);

        // Then worker2 should see both workers
        assertThat(result2.activeWorkers()).isEqualTo(2);

        // And worker2 should acquire approximately half of the leases
        assertThat(result2.idealShare()).isCloseTo(result2.activePartitions() / 2.0, within(0.5));
        assertThat(result2.addedLeases()).isNotEmpty();

        // When worker1 checks in again
        final var result1Again = workerDao.checkIn(
                worker1.nodeId(),
                worker1.consumerGroupId(),
                worker1.weight(),
                5);

        // Then worker1 should release some leases to achieve fair distribution
        assertThat(result1Again.activeWorkers()).isEqualTo(2);
        assertThat(result1Again.removedLeases()).isNotEmpty();

        // Verify final distribution is approximately fair
        final var worker1Leases = new AtomicInteger(0);
        final var worker2Leases = new AtomicInteger(0);

        final var allOffsets = subscriptionOffsetDao.findAll(
                result1.addedLeases().getFirst().subscriptionId());

        for (SubscriptionOffset offset : allOffsets) {
            leaseDao.find(offset.id()).ifPresent(lease -> {
                if (lease.workerId() == worker1.id()) {
                    worker1Leases.incrementAndGet();
                } else if (lease.workerId() == updatedWorker2.id()) {
                    worker2Leases.incrementAndGet();
                }
            });
        }

        // With equal weights, each worker should have approximately the same number of leases
        double ratio = (double) worker1Leases.get() / (worker1Leases.get() + worker2Leases.get());
        assertThat(ratio).isCloseTo(0.5, within(0.2));
    }

    @Test
    void checkIn_workerExpiry_releasesLeases() throws InterruptedException {
        // Given a worker that has acquired leases
        final var worker = data.worker1();

        // When the worker checks in
        final var result = workerDao.checkIn(
                worker.nodeId(),
                worker.consumerGroupId(),
                worker.weight(),
                1); // Short lease TTL for testing

        // Then the worker should acquire leases
        assertThat(result.addedLeases()).isNotEmpty();

        // Verify leases in database
        for (SubscriptionOffset offset : result.addedLeases()) {
            assertThat(leaseDao.find(offset.id()))
                    .isPresent()
                    .get()
                    .extracting(Lease::workerId)
                    .isEqualTo(worker.id());
        }

        // Wait for leases to expire
        // Sleep for 11 seconds to ensure the worker is considered expired (default 10 seconds)
        Thread.sleep(11000);

        // When another worker checks in
        final var worker2Id = workerDao.checkIn(
                UUID.randomUUID().toString(), worker.consumerGroupId(), worker.weight(), 5).workerId();
        final var worker2 = workerDao.find(worker2Id).orElseThrow();

        final var result2 = workerDao.checkIn(
                worker2.nodeId(),
                worker2.consumerGroupId(),
                worker2.weight(),
                5);

        // Then the new worker should acquire the expired leases
        assertThat(result2.addedLeases()).isNotEmpty();

        // And the original worker should be considered expired
        assertThat(result2.activeWorkers()).isEqualTo(1);

        // Verify leases in database are now owned by worker2
        for (final var offset : result2.addedLeases()) {
            assertThat(leaseDao.find(offset.id()))
                    .isPresent()
                    .get()
                    .extracting(Lease::workerId)
                    .isEqualTo(worker2.id());
        }
    }
}