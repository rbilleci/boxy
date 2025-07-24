package boxy.core.it;

import boxy.core.dao.ConsumerGroupDao;
import boxy.core.dao.SubscriptionDao;
import boxy.core.dao.TopicDao;
import boxy.core.service.SubscriptionService;
import boxy.core.model.Subscription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import static boxy.core.it.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public class SubscriptionIT extends BaseIT {

    private ConsumerGroupDao consumerGroupDao;
    private SubscriptionService subscriptionService;
    private TopicDao topicDao;
    private TestData data;

    @BeforeEach
    void setup() {
        consumerGroupDao = new ConsumerGroupDao(dataSource);
        subscriptionService = new SubscriptionService(dataSource);
        topicDao = new TopicDao(dataSource);
        data = TestData.seed(dataSource);
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
        assertThat(new SubscriptionDao(dataSource).findAll(100, 0)).hasSize(8);
    }

    @Test
    void delete_whenDeleted_isNotPresent() {
        // UNSUBSCRIBE
        subscriptionService.unsubscribe(TENANT_1, CONSUMER_GROUP_A, TOPIC_A);
        subscriptionService.unsubscribe(TENANT_1, CONSUMER_GROUP_A, TOPIC_B);
        subscriptionService.unsubscribe(TENANT_2, CONSUMER_GROUP_A, TOPIC_C);
        subscriptionService.unsubscribe(TENANT_2, CONSUMER_GROUP_A, TOPIC_D);
        // VALIDATE
        assertThat(new SubscriptionDao(dataSource).findAll(100, 0)).hasSize(4);
    }

    @Test
    void delete_whenDeletedMultipleTimes_allNotPresent() {
        // UNSUBSCRIBE AND VALIDATE 1
        subscriptionService.unsubscribe(TENANT_1, CONSUMER_GROUP_A, TOPIC_A, TOPIC_B);
        assertThat(new SubscriptionDao(dataSource).findAll(100, 0)).hasSize(6);
        // UNSUBSCRIBE AND VALIDATE 2
        subscriptionService.unsubscribe(TENANT_2, CONSUMER_GROUP_A, TOPIC_C, TOPIC_D);
        assertThat(new SubscriptionDao(dataSource).findAll(100, 0)).hasSize(4);
    }


    @Test
    void deleteAll_whenDeleted_otherConsumerGroupsUnaffected() {
        // VALIDATE
        assertThat(new SubscriptionDao(dataSource).findAll(100, 0))
                .hasSize(8)
                .extracting(Subscription::consumerGroupId)
                .containsAll(data.subscriptions().stream().map(Subscription::consumerGroupId).toList());
        // UNSUBSCRIBE CG1
        subscriptionService.unsubscribe(TENANT_1, CONSUMER_GROUP_A, TOPIC_A);
        subscriptionService.unsubscribe(TENANT_1, CONSUMER_GROUP_A, TOPIC_B);
        subscriptionService.unsubscribe(TENANT_2, CONSUMER_GROUP_A, TOPIC_C);
        subscriptionService.unsubscribe(TENANT_2, CONSUMER_GROUP_A, TOPIC_D);
        // VALIDATE
        assertThat(new SubscriptionDao(dataSource).findAll(100, 0))
                .hasSize(4);
    }

    @Test
    void findAll_whenLimitAndOffset_returnsCorrectItems() {
        final var s1 = subscriptionService.find(TENANT_1, CONSUMER_GROUP_A, TOPIC_A).orElseThrow().id();
        final var s2 = subscriptionService.find(TENANT_1, CONSUMER_GROUP_A, TOPIC_B).orElseThrow().id();
        final var s3 = subscriptionService.find(TENANT_1, CONSUMER_GROUP_B, TOPIC_A).orElseThrow().id();
        final var s4 = subscriptionService.find(TENANT_1, CONSUMER_GROUP_B, TOPIC_B).orElseThrow().id();
        // PAGE 1
        assertThat(new SubscriptionDao(dataSource).findAll(2, 0))
                .extracting(Subscription::id)
                .containsExactlyInAnyOrder(s1, s2);
        // PAGE 2
        assertThat(new SubscriptionDao(dataSource).findAll(2, 2))
                .extracting(Subscription::id)
                .containsExactlyInAnyOrder(s3, s4);

    }
}
