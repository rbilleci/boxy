package boxy.persistence.it;

import boxy.persistence.dao.ConsumerGroupDao;
import boxy.persistence.dao.SubscriptionDao;
import boxy.persistence.dao.TopicDao;
import boxy.persistence.model.Subscription;
import boxy.persistence.model.Topic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public class SubscriptionIT extends BaseIT {

    private static final String TENANT = "t-1";
    private static final String CONSUMER_GROUP_A = "g-a";
    private static final String CONSUMER_GROUP_B = "g-b";
    private static final String TOPIC_A = "t-a";
    private static final String TOPIC_B = "t-b";
    private static final String TOPIC_C = "t-c";
    private static final String TOPIC_D = "t-d";

    private ConsumerGroupDao consumerGroupDao;
    private SubscriptionDao subscriptionDao;
    private TopicDao topicDao;

    @BeforeEach
    void setup() {
        consumerGroupDao = jdbi.onDemand(ConsumerGroupDao.class);
        subscriptionDao = jdbi.onDemand(SubscriptionDao.class);
        topicDao = jdbi.onDemand(TopicDao.class);
    }

    @Test
    void create_whenCreated_isPresent() {
        // REGISTER
        final var topicId = topicDao.create(TENANT, TOPIC_A, 16);
        final var consumerGroupId = consumerGroupDao.create(TENANT, CONSUMER_GROUP_A);
        // SUBSCRIBE
        final var subscriptionId = subscriptionDao.subscribe(TENANT, CONSUMER_GROUP_A, TOPIC_A);
        final var subscription = subscriptionDao.find(subscriptionId);
        // VALIDATE
        assertThat(subscription).isPresent().get().extracting(Subscription::topicId).isEqualTo(topicId);
        assertThat(subscription).isPresent().get().extracting(Subscription::consumerGroupId).isEqualTo(consumerGroupId);
    }

    @Test
    void created_whenCreatedMultipleTimes_allPresent() {
        // REGISTER
        final var topicA = topicDao.create(TENANT, TOPIC_A, 16);
        final var topicB = topicDao.create(TENANT, TOPIC_B, 16);
        final var topicC = topicDao.create(TENANT, TOPIC_C, 16);
        final var topicD = topicDao.create(TENANT, TOPIC_D, 16);
        consumerGroupDao.create(TENANT, CONSUMER_GROUP_A);
        // SUBSCRIBE
        subscriptionDao.subscribe(TENANT, CONSUMER_GROUP_A, TOPIC_A);
        subscriptionDao.subscribe(TENANT, CONSUMER_GROUP_A, TOPIC_B);
        subscriptionDao.subscribe(TENANT, CONSUMER_GROUP_A, TOPIC_C);
        subscriptionDao.subscribe(TENANT, CONSUMER_GROUP_A, TOPIC_D);
        // VALIDATE
        assertThat(subscriptionDao.findAll(100, 0))
                .hasSize(4)
                .extracting(Subscription::topicId)
                .containsExactlyInAnyOrder(topicA, topicB, topicC, topicD);
    }

    @Test
    void delete_whenDeleted_isNotPresent() {
        // REGISTER
        topicDao.create(TENANT, TOPIC_A, 16);
        topicDao.create(TENANT, TOPIC_B, 16);
        topicDao.create(TENANT, TOPIC_C, 16);
        topicDao.create(TENANT, TOPIC_D, 16);
        consumerGroupDao.create(TENANT, CONSUMER_GROUP_A);
        // SUBSCRIBE
        subscriptionDao.subscribe(TENANT, CONSUMER_GROUP_A, TOPIC_A);
        subscriptionDao.subscribe(TENANT, CONSUMER_GROUP_A, TOPIC_B);
        subscriptionDao.subscribe(TENANT, CONSUMER_GROUP_A, TOPIC_C);
        subscriptionDao.subscribe(TENANT, CONSUMER_GROUP_A, TOPIC_D);
        // UNSUBSCRIBE
        subscriptionDao.unsubscribe(TENANT, CONSUMER_GROUP_A, TOPIC_A);
        subscriptionDao.unsubscribe(TENANT, CONSUMER_GROUP_A, TOPIC_B);
        subscriptionDao.unsubscribe(TENANT, CONSUMER_GROUP_A, TOPIC_C);
        subscriptionDao.unsubscribe(TENANT, CONSUMER_GROUP_A, TOPIC_D);
        // VALIDATE
        assertThat(subscriptionDao.findAll(100, 0)).isEmpty();
    }

    @Test
    void delete_whenDeletedMultipleTimes_allNotPresent() {
        // REGISTER
        topicDao.create(TENANT, TOPIC_A, 16);
        topicDao.create(TENANT, TOPIC_B, 16);
        topicDao.create(TENANT, TOPIC_C, 16);
        topicDao.create(TENANT, TOPIC_D, 16);
        consumerGroupDao.create(TENANT, CONSUMER_GROUP_A);
        // SUBSCRIBE
        subscriptionDao.subscribe(TENANT, CONSUMER_GROUP_A, TOPIC_A, TOPIC_B, TOPIC_C, TOPIC_D);
        // UNSUBSCRIBE AND VALIDATE 1
        subscriptionDao.unsubscribe(TENANT, CONSUMER_GROUP_A, TOPIC_A, TOPIC_B);
        assertThat(subscriptionDao.findAll(100, 0)).hasSize(2);
        // UNSUBSCRIBE AND VALIDATE 2
        subscriptionDao.unsubscribe(TENANT, CONSUMER_GROUP_A, TOPIC_C, TOPIC_D);
        assertThat(subscriptionDao.findAll(100, 0)).isEmpty();
    }


    @Test
    void deleteAll_whenDeleted_otherConsumerGroupsUnaffected() {
        // REGISTER
        topicDao.create(TENANT, TOPIC_A, 16);
        topicDao.create(TENANT, TOPIC_B, 16);
        topicDao.create(TENANT, TOPIC_C, 16);
        topicDao.create(TENANT, TOPIC_D, 16);
        final var cg1 = consumerGroupDao.create(TENANT, CONSUMER_GROUP_A);
        final var cg2 = consumerGroupDao.create(TENANT, CONSUMER_GROUP_B);
        // SUBSCRIBE CG1
        subscriptionDao.subscribe(TENANT, CONSUMER_GROUP_A, TOPIC_A);
        subscriptionDao.subscribe(TENANT, CONSUMER_GROUP_A, TOPIC_B);
        subscriptionDao.subscribe(TENANT, CONSUMER_GROUP_A, TOPIC_C);
        subscriptionDao.subscribe(TENANT, CONSUMER_GROUP_A, TOPIC_D);
        // SUBSCRIBE CG2
        subscriptionDao.subscribe(TENANT, CONSUMER_GROUP_B, TOPIC_A);
        subscriptionDao.subscribe(TENANT, CONSUMER_GROUP_B, TOPIC_B);
        subscriptionDao.subscribe(TENANT, CONSUMER_GROUP_B, TOPIC_C);
        subscriptionDao.subscribe(TENANT, CONSUMER_GROUP_B, TOPIC_D);
        // VALIDATE
        assertThat(subscriptionDao.findAll(100, 0))
                .hasSize(8)
                .extracting(Subscription::consumerGroupId)
                .containsExactlyInAnyOrder(cg1, cg1, cg1, cg1, cg2, cg2, cg2, cg2);
        // UNSUBSCRIBE CG1
        subscriptionDao.unsubscribe(TENANT, CONSUMER_GROUP_A, TOPIC_A);
        subscriptionDao.unsubscribe(TENANT, CONSUMER_GROUP_A, TOPIC_B);
        subscriptionDao.unsubscribe(TENANT, CONSUMER_GROUP_A, TOPIC_C);
        subscriptionDao.unsubscribe(TENANT, CONSUMER_GROUP_A, TOPIC_D);
        // VALIDATE
        assertThat(subscriptionDao.findAll(100, 0))
                .hasSize(4)
                .extracting(Subscription::consumerGroupId)
                .containsExactlyInAnyOrder(cg2, cg2, cg2, cg2);
    }

    @Test
    void findAll_whenLimitAndOffset_returnsCorrectItems() {
        // REGISTER
        topicDao.create(TENANT, TOPIC_A, 16);
        topicDao.create(TENANT, TOPIC_B, 16);
        topicDao.create(TENANT, TOPIC_C, 16);
        topicDao.create(TENANT, TOPIC_D, 16);
        final var cg1 = consumerGroupDao.create(TENANT, CONSUMER_GROUP_A);
        final var cg2 = consumerGroupDao.create(TENANT, CONSUMER_GROUP_B);
        // SUBSCRIBE CG1
        final var s1 = subscriptionDao.subscribe(TENANT, CONSUMER_GROUP_A, TOPIC_A);
        final var s2 = subscriptionDao.subscribe(TENANT, CONSUMER_GROUP_A, TOPIC_B);
        // SUBSCRIBE CG2
        final var s3 = subscriptionDao.subscribe(TENANT, CONSUMER_GROUP_B, TOPIC_A);
        final var s4 = subscriptionDao.subscribe(TENANT, CONSUMER_GROUP_B, TOPIC_B);
        // PAGE 1
        assertThat(subscriptionDao.findAll(2, 0))
                .extracting(Subscription::id)
                .containsExactlyInAnyOrder(s1, s2);
        // PAGE 2
        assertThat(subscriptionDao.findAll(2, 2))
                .extracting(Subscription::id)
                .containsExactlyInAnyOrder(s3, s4);

    }
}
