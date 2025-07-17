package boxy.persistence.it;

import boxy.persistence.dao.*;
import boxy.persistence.model.Partition;
import boxy.persistence.model.SubscriptionOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public class SubscriptionOffsetIT extends BaseIT {

    private static final String CONSUMER_GROUP_A = "cga";
    private static final String TENANT = "t1";
    private static final String TOPIC = "topic";
    private static final int DEFAULT_PARTITIONS = 16;


    private ConsumerGroupDao consumerGroupDao;
    private SubscriptionDao subscriptionDao;
    private SubscriptionOffsetDao subscriptionOffsetDao;
    private TopicDao topicDao;

    @BeforeEach
    void setup() {
        consumerGroupDao = jdbi.onDemand(ConsumerGroupDao.class);
        subscriptionDao = jdbi.onDemand(SubscriptionDao.class);
        subscriptionOffsetDao = jdbi.onDemand(SubscriptionOffsetDao.class);
        topicDao = jdbi.onDemand(TopicDao.class);
    }

    @Test
    void commit_whenOffsetDoesNotExist_insertsNewRow() {
        topicDao.create(TENANT, TOPIC, DEFAULT_PARTITIONS);
        consumerGroupDao.create(TENANT, CONSUMER_GROUP_A);
        final var subscriptionId = subscriptionDao.subscribe(TENANT, CONSUMER_GROUP_A, TOPIC);
        final var partitionId = resolveAnySubscriptionOffset(subscriptionId).partitionId();
        final var subscriptionOffset = subscriptionOffsetDao.find(subscriptionId, partitionId).orElseThrow();

        // COMMIT HWM
        subscriptionOffsetDao.commit(subscriptionOffset.id(), 100L);
        assertThat(subscriptionOffsetDao.find(subscriptionId, partitionId))
                .isPresent()
                .get()
                .extracting(SubscriptionOffset::committedOffset)
                .isEqualTo(100L);
    }

    @Test
    void commit_whenOffsetIsHigher_updatesExistingRow() {
        topicDao.create(TENANT, TOPIC, DEFAULT_PARTITIONS);
        consumerGroupDao.create(TENANT, CONSUMER_GROUP_A);
        final var subscriptionId = subscriptionDao.subscribe(TENANT, CONSUMER_GROUP_A, TOPIC);
        final var partitionId = resolveAnySubscriptionOffset(subscriptionId).partitionId();
        final var subscriptionOffset = subscriptionOffsetDao.find(subscriptionId, partitionId).orElseThrow();

        // COMMIT HWM
        subscriptionOffsetDao.commit(subscriptionOffset.id(), 100L);
        subscriptionOffsetDao.commit(subscriptionOffset.id(), 101L);
        subscriptionOffsetDao.commit(subscriptionOffset.id(), 102L);
        assertThat(subscriptionOffsetDao.find(subscriptionId, partitionId))
                .isPresent()
                .get()
                .extracting(SubscriptionOffset::committedOffset)
                .isEqualTo(102L);
    }

    @Test
    void commit_whenOffsetIsLower_doesNotUpdateExistingRow() {
        topicDao.create(TENANT, TOPIC, DEFAULT_PARTITIONS);
        consumerGroupDao.create(TENANT, CONSUMER_GROUP_A);
        final var subscriptionId = subscriptionDao.subscribe(TENANT, CONSUMER_GROUP_A, TOPIC);
        final var partitionId = resolveAnySubscriptionOffset(subscriptionId).partitionId();
        final var subscriptionOffset = subscriptionOffsetDao.find(subscriptionId, partitionId).orElseThrow();

        // COMMIT HWM
        subscriptionOffsetDao.commit(subscriptionOffset.id(), 100L);
        subscriptionOffsetDao.commit(subscriptionOffset.id(), 10L);
        assertThat(subscriptionOffsetDao.find(subscriptionId, partitionId))
                .isPresent()
                .get()
                .extracting(SubscriptionOffset::committedOffset)
                .isEqualTo(100L);
    }

    @Test
    void commit_whenOffsetIsEqual_doesNotUpdateExistingRow() {
        topicDao.create(TENANT, TOPIC, DEFAULT_PARTITIONS);
        consumerGroupDao.create(TENANT, CONSUMER_GROUP_A);
        final var subscriptionId = subscriptionDao.subscribe(TENANT, CONSUMER_GROUP_A, TOPIC);
        final var partitionId = resolveAnySubscriptionOffset(subscriptionId).partitionId();
        final var subscriptionOffset = subscriptionOffsetDao.find(subscriptionId, partitionId).orElseThrow();

        // COMMIT HWM
        subscriptionOffsetDao.commit(subscriptionOffset.id(), 100L);
        subscriptionOffsetDao.commit(subscriptionOffset.id(), 100L);
        assertThat(subscriptionOffsetDao.find(subscriptionId, partitionId))
                .isPresent()
                .get()
                .extracting(SubscriptionOffset::committedOffset)
                .isEqualTo(100L);

    }

    private SubscriptionOffset resolveAnySubscriptionOffset(final long subscriptionId) {
        return subscriptionOffsetDao.findAll(subscriptionId).getFirst();
    }
}
