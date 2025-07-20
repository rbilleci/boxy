package boxy.persistence.it;

import boxy.persistence.dao.*;
import boxy.persistence.model.Lease;
import boxy.persistence.model.SubscriptionOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public class LeaseIT extends BaseIT {

    private static final String CONSUMER_GROUP_A = "cga";
    private static final String TENANT = "t1";
    private static final String TOPIC = "topic";
    private static final int DEFAULT_PARTITIONS = 16;
    private static final String PARTY_1 = UUID.randomUUID().toString();
    private static final String PARTY_2 = UUID.randomUUID().toString();

    private ConsumerGroupDao consumerGroupDao;
    private SubscriptionDao subscriptionDao;
    private SubscriptionOffsetDao subscriptionOffsetDao;
    private TopicDao topicDao;
    private LeaseDao leaseDao;
    private WorkerDao workerDao;

    @BeforeEach
    void setup() {
        consumerGroupDao = jdbi.onDemand(ConsumerGroupDao.class);
        subscriptionDao = jdbi.onDemand(SubscriptionDao.class);
        subscriptionOffsetDao = jdbi.onDemand(SubscriptionOffsetDao.class);
        topicDao = jdbi.onDemand(TopicDao.class);
        leaseDao = jdbi.onDemand(LeaseDao.class);
        workerDao = jdbi.onDemand(WorkerDao.class);
    }

    private TestingData seedTestingData() {
        final var topic = topicDao.find(topicDao.create(TENANT, TOPIC, DEFAULT_PARTITIONS)).orElseThrow();
        final var consumerGroup = consumerGroupDao.find(consumerGroupDao.create(TENANT, CONSUMER_GROUP_A)).orElseThrow();
        final var subscription = subscriptionDao.find(subscriptionDao.subscribe(TENANT, CONSUMER_GROUP_A, TOPIC)).orElseThrow();
        final var partitionId = resolveAnySubscriptionOffset(subscription.id()).partitionId();
        final var subscriptionOffset = subscriptionOffsetDao.find(
                subscription.id(),
                partitionId).orElseThrow();
        final var worker1 = workerDao.find(workerDao.register(PARTY_1, consumerGroup.id(), 1)).orElseThrow();
        final var worker2 = workerDao.find(workerDao.register(PARTY_2, consumerGroup.id(), 1)).orElseThrow();
        return new TestingData(
                topic,
                consumerGroup,
                subscription,
                subscriptionOffset,
                worker1,
                worker2);
    }

    @Test
    void acquire_whenNoLeaseExists_returnsAndCreatesLease() {
        // Attempt to acquire a lease for 10 seconds
        // check the return value is equal to tru (a successful lease)
        // then verify the owner
        final var data = seedTestingData();
        assertThat(leaseDao.acquire(data.subscriptionOffset().id(), data.worker1().id(), 10)).isTrue();
        assertThat(leaseDao.find(data.subscriptionOffset().id()))
                .isPresent()
                .get()
                .extracting(Lease::workerId).isEqualTo(data.worker1().id());
    }

    @Test
    void acquire_whenActiveLeaseExists_returnsAndDoesNotChangeLease() {
        // Attempt to acquire a lease for 10 seconds
        // check the return value is equal to true (a successful lease)
        // then verify the owner
        final var data = seedTestingData();
        assertThat(leaseDao.acquire(data.subscriptionOffset().id(), data.worker1().id(), 10)).isTrue();
        assertThat(leaseDao.find(data.subscriptionOffset().id()))
                .isPresent()
                .get()
                .extracting(Lease::workerId).isEqualTo(data.worker1().id());

        // Then, another party attempts to acquire the lease,
        // the lease must be unchanged.
        assertThat(leaseDao.acquire(data.subscriptionOffset().id(), data.worker2().id(), 10)).isTrue();
        assertThat(leaseDao.find(data.subscriptionOffset().id()))
                .isPresent()
                .get()
                .extracting(Lease::workerId).isEqualTo(data.worker1().id());
    }

    @Test
    void acquire_whenExpiredLeaseExists_returnsTrueAndChangesOwner() throws InterruptedException {
        final var data = seedTestingData();
        // Attempt to acquire a lease for **1** second
        assertThat(leaseDao.acquire(data.subscriptionOffset().id(), data.worker1().id(), 0)).isTrue();
        assertThat(leaseDao.find(data.subscriptionOffset().id()))
                .isPresent()
                .get()
                .extracting(Lease::workerId).isEqualTo(data.worker1().id());
        Thread.sleep(1000);
        // Then, another party attempts to acquire the lease
        assertThat(leaseDao.acquire(data.subscriptionOffset().id(), data.worker2().id(), 10)).isTrue();
        assertThat(leaseDao.find(data.subscriptionOffset().id()))
                .isPresent()
                .get()
                .extracting(Lease::workerId).isEqualTo(data.worker2().id());
    }

    @Test
    void renew_whenCallerIsOwner_returnsTrue() {
        final var data = seedTestingData();
        // Attempt to acquire a lease
        assertThat(leaseDao.acquire(data.subscriptionOffset().id(), data.worker1().id(), 10)).isTrue();
        assertThat(leaseDao.find(data.subscriptionOffset().id()))
                .isPresent()
                .get()
                .extracting(Lease::workerId).isEqualTo(data.worker1().id());
        // RENEW FOR 10 SECONDS
        assertThat(leaseDao.renew(data.subscriptionOffset().id(), data.worker1().id(), 10)).isTrue();
    }

    @Test
    void renew_whenCallerIsNotOwner_returnsFalse() {
        final var data = seedTestingData();
        // WORKER 1 ACQUIRES LEASE
        assertThat(leaseDao.acquire(data.subscriptionOffset().id(), data.worker1().id(), 10)).isTrue();
        assertThat(leaseDao.find(data.subscriptionOffset().id()))
                .isPresent()
                .get()
                .extracting(Lease::workerId).isEqualTo(data.worker1().id());
        // RENEW FOR 10 SECONDS, BY ANOTHER PARTY
        assertThat(leaseDao.renew(data.subscriptionOffset().id(), data.worker2().id(), 10)).isFalse();
    }


    @Test
    void delete_whenCallerIsOwner_returnsAndDeletesLease() {
        final var data = seedTestingData();
        // WORKER 1 ACQUIRES LEASE
        assertThat(leaseDao.acquire(data.subscriptionOffset().id(), data.worker1().id(), 10)).isTrue();
        assertThat(leaseDao.find(data.subscriptionOffset().id()))
                .isPresent()
                .get()
                .extracting(Lease::workerId).isEqualTo(data.worker1().id());
        // RELEASE
        leaseDao.release(data.subscriptionOffset().id(), data.worker1().id());
        assertThat(leaseDao.find(data.subscriptionOffset().id())).isNotPresent();
    }

    @Test
    void delete_whenCallerIsNotOwner_returnsAndLeaseRemains() {
        final var data = seedTestingData();
        // Attempt to acquire a lease
        assertThat(leaseDao.acquire(data.subscriptionOffset().id(), data.worker1().id(), 10)).isTrue();
        assertThat(leaseDao.find(data.subscriptionOffset().id()))
                .isPresent()
                .get()
                .extracting(Lease::workerId).isEqualTo(data.worker1().id());
        // RELEASE BY PARTY 2!!!
        leaseDao.release(data.subscriptionOffset().id(), data.worker2().id());
        // PARTY 1 STILL OWNS IT!!!
        assertThat(leaseDao.find(data.subscriptionOffset().id()))
                .isPresent()
                .get()
                .extracting(Lease::workerId)
                .isEqualTo(data.worker1().id());
    }

    private SubscriptionOffset resolveAnySubscriptionOffset(final long subscriptionId) {
        return subscriptionOffsetDao.findAll(subscriptionId).getFirst();
    }

}
