package boxy.persistence.it;

import boxy.persistence.dao.*;
import boxy.persistence.model.SubscriptionOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public class ProcessorIT extends BaseIT {

    private static final String CONSUMER_GROUP_A = "cga";
    private static final String CONSUMER_GROUP_B = "cgb";
    private static final String TENANT_1 = "t1";
    private static final String TENANT_2 = "t2";
    private static final String TOPIC_A = "topic-a";
    private static final String TOPIC_B = "topic-b";
    private static final String TOPIC_C = "topic-c";
    private static final String TOPIC_D = "topic-d";
    private static final int DEFAULT_PARTITIONS = 16;
    private static final String DATA = """
            {"key": "value"}
            """;

    private ConsumerGroupDao consumerGroupDao;
    private SubscriptionDao subscriptionDao;
    private EventDao eventDao;
    private TopicDao topicDao;
    private LeaseDao leaseDao;

    @BeforeEach
    void setup() {
        consumerGroupDao = jdbi.onDemand(ConsumerGroupDao.class);
        subscriptionDao = jdbi.onDemand(SubscriptionDao.class);
        eventDao = jdbi.onDemand(EventDao.class);
        topicDao = jdbi.onDemand(TopicDao.class);
        leaseDao = jdbi.onDemand(LeaseDao.class);
    }

    @Test
    void leasesAvailable_whenViewHasOneItem_returnsListWithOneItem() {
        topicDao.create(TENANT_1, TOPIC_A, DEFAULT_PARTITIONS);
        consumerGroupDao.create(TENANT_1, CONSUMER_GROUP_A);
        final var subscriptionId = subscriptionDao.subscribe(TENANT_1, CONSUMER_GROUP_A, TOPIC_A);
        // PUBLISH
        eventDao.publish(TENANT_1, TOPIC_A, "partitionKey", DATA);
        // VALIDATE
        final var subscriptionOffsets = leaseDao.leasesAvailable(100, 0);
        assertThat(subscriptionOffsets).hasSize(1);
        final var result = subscriptionOffsets.getFirst();
        assertThat(result.subscriptionId()).isEqualTo(subscriptionId);
        assertThat(result.committedOffset()).isEqualTo(0L);
    }

    @Test
    void leasesAvailable_whenViewHasMultipleItems_returnsAllItems() {
        topicDao.create(TENANT_1, TOPIC_A, DEFAULT_PARTITIONS);
        topicDao.create(TENANT_1, TOPIC_B, DEFAULT_PARTITIONS);
        topicDao.create(TENANT_2, TOPIC_C, DEFAULT_PARTITIONS);
        topicDao.create(TENANT_2, TOPIC_D, DEFAULT_PARTITIONS);
        // TENANT 1
        consumerGroupDao.create(TENANT_1, CONSUMER_GROUP_A);
        consumerGroupDao.create(TENANT_1, CONSUMER_GROUP_B);
        final var s1 = subscriptionDao.subscribe(TENANT_1, CONSUMER_GROUP_A, TOPIC_A);
        final var s2 = subscriptionDao.subscribe(TENANT_1, CONSUMER_GROUP_A, TOPIC_B);
        final var s3 = subscriptionDao.subscribe(TENANT_1, CONSUMER_GROUP_B, TOPIC_A);
        final var s4 = subscriptionDao.subscribe(TENANT_1, CONSUMER_GROUP_B, TOPIC_B);
        // TENANT 2
        consumerGroupDao.create(TENANT_2, CONSUMER_GROUP_A);
        consumerGroupDao.create(TENANT_2, CONSUMER_GROUP_B);
        final var s5 = subscriptionDao.subscribe(TENANT_2, CONSUMER_GROUP_A, TOPIC_C);
        final var s6 = subscriptionDao.subscribe(TENANT_2, CONSUMER_GROUP_A, TOPIC_D);
        final var s7 = subscriptionDao.subscribe(TENANT_2, CONSUMER_GROUP_B, TOPIC_C);
        final var s8 = subscriptionDao.subscribe(TENANT_2, CONSUMER_GROUP_B, TOPIC_D);
        // PUBLISH
        eventDao.publish(TENANT_1, TOPIC_A, "pk1", DATA);
        eventDao.publish(TENANT_1, TOPIC_A, "pk2", DATA);
        eventDao.publish(TENANT_1, TOPIC_B, "pk3", DATA);
        eventDao.publish(TENANT_1, TOPIC_B, "pk4", DATA);
        eventDao.publish(TENANT_2, TOPIC_C, "pk5", DATA);
        eventDao.publish(TENANT_2, TOPIC_C, "pk6", DATA);
        eventDao.publish(TENANT_2, TOPIC_D, "pk7", DATA);
        eventDao.publish(TENANT_2, TOPIC_D, "pk8", DATA);
        // VALIDATE
        final var leasable = leaseDao.leasesAvailable(100, 0);
        assertThat(leasable)
                .hasSize(16)
                .extracting(SubscriptionOffset::subscriptionId)
                .containsExactlyInAnyOrder(
                        s1, s2, s3, s4, s5, s6, s7, s8,
                        s1, s2, s3, s4, s5, s6, s7, s8);
    }

    @Test
    void leasesAvailable_whenViewIsEmpty_returnsEmptyList() {
        assertThat(leaseDao.leasesAvailable(100, 0))
                .isNotNull()
                .isEmpty();
    }

}
