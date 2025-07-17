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

    @BeforeEach
    void setup() {
        consumerGroupDao = jdbi.onDemand(ConsumerGroupDao.class);
        subscriptionDao = jdbi.onDemand(SubscriptionDao.class);
        subscriptionOffsetDao = jdbi.onDemand(SubscriptionOffsetDao.class);
        topicDao = jdbi.onDemand(TopicDao.class);
        leaseDao = jdbi.onDemand(LeaseDao.class);
    }

    @Test
    void acquire_whenNoLeaseExists_returnsAndCreatesLease() {
        // Attempt to acquire a lease for 10 seconds
        // check the return value is equal to tru (a successful lease)
        // then verify the owner
        topicDao.create(TENANT, TOPIC, DEFAULT_PARTITIONS);
        consumerGroupDao.create(TENANT, CONSUMER_GROUP_A);
        final var subscriptionId = subscriptionDao.subscribe(TENANT, CONSUMER_GROUP_A, TOPIC);
        final var partitionId = resolveAnySubscriptionOffset(subscriptionId).partitionId();
        final var subscriptionOffset = subscriptionOffsetDao.find(subscriptionId, partitionId).orElseThrow();
        assertThat(leaseDao.acquire(subscriptionOffset.id(), PARTY_1, 10)).isTrue();
        assertThat(leaseDao.find(subscriptionOffset.id()))
                .isPresent()
                .get()
                .extracting(Lease::owner).isEqualTo(PARTY_1);
    }

    @Test
    void acquire_whenActiveLeaseExists_returnsAndDoesNotChangeLease() {
        // Attempt to acquire a lease for 10 seconds
        // check the return value is equal to tru (a successful lease)
        // then verify the owner
        topicDao.create(TENANT, TOPIC, DEFAULT_PARTITIONS);
        consumerGroupDao.create(TENANT, CONSUMER_GROUP_A);
        final var subscriptionId = subscriptionDao.subscribe(TENANT, CONSUMER_GROUP_A, TOPIC);
        final var partitionId = resolveAnySubscriptionOffset(subscriptionId).partitionId();
        final var subscriptionOffset = subscriptionOffsetDao.find(subscriptionId, partitionId).orElseThrow();
        assertThat(leaseDao.acquire(subscriptionOffset.id(), PARTY_1, 10)).isTrue();
        assertThat(leaseDao.find(subscriptionOffset.id()))
                .isPresent()
                .get()
                .extracting(Lease::owner).isEqualTo(PARTY_1);

        // Then, another party attempts to acquire the lease
        assertThat(leaseDao.acquire(subscriptionOffset.id(), PARTY_2, 10)).isFalse();
        assertThat(leaseDao.find(subscriptionOffset.id()))
                .isPresent()
                .get()
                .extracting(Lease::owner).isEqualTo(PARTY_1);
    }

    @Test
    void acquire_whenExpiredLeaseExists_returnsTrueAndChangesOwner() throws InterruptedException {
        // Attempt to acquire a lease for **1** second
        topicDao.create(TENANT, TOPIC, DEFAULT_PARTITIONS);
        consumerGroupDao.create(TENANT, CONSUMER_GROUP_A);
        final var subscriptionId = subscriptionDao.subscribe(TENANT, CONSUMER_GROUP_A, TOPIC);
        final var partitionId = resolveAnySubscriptionOffset(subscriptionId).partitionId();
        final var subscriptionOffset = subscriptionOffsetDao.find(subscriptionId, partitionId).orElseThrow();
        assertThat(leaseDao.acquire(subscriptionOffset.id(), PARTY_1, 0)).isTrue();
        assertThat(leaseDao.find(subscriptionOffset.id()))
                .isPresent()
                .get()
                .extracting(Lease::owner).isEqualTo(PARTY_1);
        Thread.sleep(1000);

        // Then, another party attempts to acquire the lease
        assertThat(leaseDao.acquire(subscriptionOffset.id(), PARTY_2, 10)).isTrue();
        assertThat(leaseDao.find(subscriptionOffset.id()))
                .isPresent()
                .get()
                .extracting(Lease::owner).isEqualTo(PARTY_2);
    }

    @Test
    void renew_whenCallerIsOwner_returnsTrue() {
        // Attempt to acquire a lease
        topicDao.create(TENANT, TOPIC, DEFAULT_PARTITIONS);
        consumerGroupDao.create(TENANT, CONSUMER_GROUP_A);
        final var subscriptionId = subscriptionDao.subscribe(TENANT, CONSUMER_GROUP_A, TOPIC);
        final var partitionId = resolveAnySubscriptionOffset(subscriptionId).partitionId();
        final var subscriptionOffset = subscriptionOffsetDao.find(subscriptionId, partitionId).orElseThrow();
        assertThat(leaseDao.acquire(subscriptionOffset.id(), PARTY_1, 10)).isTrue();
        assertThat(leaseDao.find(subscriptionOffset.id()))
                .isPresent()
                .get()
                .extracting(Lease::owner).isEqualTo(PARTY_1);
        // RENEW FOR 10 SECONDS
        assertThat(leaseDao.renew(subscriptionOffset.id(), PARTY_1, 10)).isTrue();
    }

    @Test
    void renew_whenCallerIsNotOwner_returnsFalse() {
        // Attempt to acquire a lease
        topicDao.create(TENANT, TOPIC, DEFAULT_PARTITIONS);
        consumerGroupDao.create(TENANT, CONSUMER_GROUP_A);
        final var subscriptionId = subscriptionDao.subscribe(TENANT, CONSUMER_GROUP_A, TOPIC);
        final var partitionId = resolveAnySubscriptionOffset(subscriptionId).partitionId();
        final var subscriptionOffset = subscriptionOffsetDao.find(subscriptionId, partitionId).orElseThrow();
        assertThat(leaseDao.acquire(subscriptionOffset.id(), PARTY_1, 10)).isTrue();
        assertThat(leaseDao.find(subscriptionOffset.id()))
                .isPresent()
                .get()
                .extracting(Lease::owner).isEqualTo(PARTY_1);
        // RENEW FOR 10 SECONDS, BY ANOTHER PARTY
        assertThat(leaseDao.renew(subscriptionOffset.id(), PARTY_2, 10)).isFalse();
    }


    @Test
    void delete_whenCallerIsOwner_returnsAndDeletesLease() {
        // Attempt to acquire a lease
        topicDao.create(TENANT, TOPIC, DEFAULT_PARTITIONS);
        consumerGroupDao.create(TENANT, CONSUMER_GROUP_A);
        final var subscriptionId = subscriptionDao.subscribe(TENANT, CONSUMER_GROUP_A, TOPIC);
        final var partitionId = resolveAnySubscriptionOffset(subscriptionId).partitionId();
        final var subscriptionOffset = subscriptionOffsetDao.find(subscriptionId, partitionId).orElseThrow();
        assertThat(leaseDao.acquire(subscriptionOffset.id(), PARTY_1, 10)).isTrue();
        assertThat(leaseDao.find(subscriptionOffset.id()))
                .isPresent()
                .get()
                .extracting(Lease::owner).isEqualTo(PARTY_1);
        // RELEASE
        leaseDao.release(subscriptionOffset.id(), PARTY_1);
        assertThat(leaseDao.find(subscriptionOffset.id())).isNotPresent();
    }

    @Test
    void delete_whenCallerIsNotOwner_returnsAndLeaseRemains() {
        // Attempt to acquire a lease
        topicDao.create(TENANT, TOPIC, DEFAULT_PARTITIONS);
        consumerGroupDao.create(TENANT, CONSUMER_GROUP_A);
        final var subscriptionId = subscriptionDao.subscribe(TENANT, CONSUMER_GROUP_A, TOPIC);
        final var partitionId = resolveAnySubscriptionOffset(subscriptionId).partitionId();
        final var subscriptionOffset = subscriptionOffsetDao.find(subscriptionId, partitionId).orElseThrow();
        assertThat(leaseDao.acquire(subscriptionOffset.id(), PARTY_1, 10)).isTrue();
        assertThat(leaseDao.find(subscriptionOffset.id()))
                .isPresent()
                .get()
                .extracting(Lease::owner).isEqualTo(PARTY_1);
        // RELEASE BY PARTY 2!!!
        leaseDao.release(subscriptionOffset.id(), PARTY_2);
        // PARTY 1 STILL OWNS IT!!!
        assertThat(leaseDao.find(subscriptionOffset.id()))
                .isPresent()
                .get()
                .extracting(Lease::owner)
                .isEqualTo(PARTY_1);
    }

    private SubscriptionOffset resolveAnySubscriptionOffset(final long subscriptionId) {
        return subscriptionOffsetDao.findAll(subscriptionId).getFirst();
    }

}
