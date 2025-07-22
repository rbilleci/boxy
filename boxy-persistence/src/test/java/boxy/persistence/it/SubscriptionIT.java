package boxy.persistence.it;

import boxy.persistence.dao.ConsumerGroupDao;
import boxy.persistence.dao.TopicDao;
import boxy.persistence.service.SubscriptionService;
import boxy.persistence.model.Subscription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import static boxy.persistence.it.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public class SubscriptionIT extends BaseIT {

    private ConsumerGroupDao consumerGroupDao;
    private SubscriptionService subscriptionService;
    private TopicDao topicDao;
    private TestData data;

    @BeforeEach
    void setup() {
        consumerGroupDao = jdbi.onDemand(ConsumerGroupDao.class);
        subscriptionService = new SubscriptionService(jdbi);
        topicDao = jdbi.onDemand(TopicDao.class);
        data = TestData.seed(jdbi);
    }

    @Test
    void create_whenCreated_isPresent() {
        // VALIDATE
        final var subscription = subscriptionService.find(TENANT_1, CONSUMER_GROUP_A, TOPIC_A);
        final var topicId = topicDao.find(TENANT_1, TOPIC_A).orElseThrow().id();
        final var consumerGroupId = consumerGroupDao.find(TENANT_1, CONSUMER_GROUP_A).orElseThrow().id();
        assertThat(subscription).isPresent().get().extracting(Subscription::topicId).isEqualTo(topicId);
        assertThat(subscription).isPresent().get().extracting(Subscription::consumerGroupId).isEqualTo(consumerGroupId);
    }

    @Test
    void created_whenCreatedMultipleTimes_allPresent() {
        // VALIDATE
        assertThat(jdbi.onDemand(SubscriptionDao.class).findAll(100, 0))
                .hasSize(8);
    }

    @Test
    void delete_whenDeleted_isNotPresent() {
        // UNSUBSCRIBE
        subscriptionService.unsubscribe(TENANT_1, CONSUMER_GROUP_A, TOPIC_A);
        subscriptionService.unsubscribe(TENANT_1, CONSUMER_GROUP_A, TOPIC_B);
        subscriptionService.unsubscribe(TENANT_2, CONSUMER_GROUP_A, TOPIC_C);
        subscriptionService.unsubscribe(TENANT_2, CONSUMER_GROUP_A, TOPIC_D);
        // VALIDATE
        assertThat(jdbi.onDemand(SubscriptionDao.class).findAll(100, 0)).hasSize(4);
    }

    @Test
    void delete_whenDeletedMultipleTimes_allNotPresent() {
        // UNSUBSCRIBE AND VALIDATE 1
        subscriptionService.unsubscribe(TENANT_1, CONSUMER_GROUP_A, TOPIC_A, TOPIC_B);
        assertThat(jdbi.onDemand(SubscriptionDao.class).findAll(100, 0)).hasSize(6);
        // UNSUBSCRIBE AND VALIDATE 2
        subscriptionService.unsubscribe(TENANT_2, CONSUMER_GROUP_A, TOPIC_C, TOPIC_D);
        assertThat(jdbi.onDemand(SubscriptionDao.class).findAll(100, 0)).hasSize(4);
    }


    @Test
    void deleteAll_whenDeleted_otherConsumerGroupsUnaffected() {
        // VALIDATE
        assertThat(jdbi.onDemand(SubscriptionDao.class).findAll(100, 0))
                .hasSize(8)
                .extracting(Subscription::consumerGroupId)
                .containsAll(data.subscriptions().stream().map(Subscription::consumerGroupId).toList());
        // UNSUBSCRIBE CG1
        subscriptionService.unsubscribe(TENANT_1, CONSUMER_GROUP_A, TOPIC_A);
        subscriptionService.unsubscribe(TENANT_1, CONSUMER_GROUP_A, TOPIC_B);
        subscriptionService.unsubscribe(TENANT_2, CONSUMER_GROUP_A, TOPIC_C);
        subscriptionService.unsubscribe(TENANT_2, CONSUMER_GROUP_A, TOPIC_D);
        // VALIDATE
        assertThat(jdbi.onDemand(SubscriptionDao.class).findAll(100, 0))
                .hasSize(4);
    }

    @Test
    void findAll_whenLimitAndOffset_returnsCorrectItems() {
        final var s1 = subscriptionService.find(TENANT_1, CONSUMER_GROUP_A, TOPIC_A).orElseThrow().id();
        final var s2 = subscriptionService.find(TENANT_1, CONSUMER_GROUP_A, TOPIC_B).orElseThrow().id();
        final var s3 = subscriptionService.find(TENANT_1, CONSUMER_GROUP_B, TOPIC_A).orElseThrow().id();
        final var s4 = subscriptionService.find(TENANT_1, CONSUMER_GROUP_B, TOPIC_B).orElseThrow().id();
        // PAGE 1
        assertThat(jdbi.onDemand(SubscriptionDao.class).findAll(2, 0))
                .extracting(Subscription::id)
                .containsExactlyInAnyOrder(s1, s2);
        // PAGE 2
        assertThat(jdbi.onDemand(SubscriptionDao.class).findAll(2, 2))
                .extracting(Subscription::id)
                .containsExactlyInAnyOrder(s3, s4);

    }
}
