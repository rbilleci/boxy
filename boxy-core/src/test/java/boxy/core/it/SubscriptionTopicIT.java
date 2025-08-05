package boxy.core.it;

import boxy.core.domain.Subscription;
import boxy.core.repository.SubscriptionRepository;
import boxy.core.repository.SubscriptionTopicRepository;
import boxy.core.repository.TopicRepository;
import boxy.core.domain.SubscriptionTopic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import static boxy.core.it.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public class SubscriptionTopicIT extends BaseIT {

    private SubscriptionRepository subscriptionRepository;
    private SubscriptionTopicRepository subscriptionTopicRepository;
    private TopicRepository topicRepository;
    private TestData data;

    @BeforeEach
    void setup() {
        subscriptionRepository = new SubscriptionRepository(dataSource);
        topicRepository = new TopicRepository(dataSource);
        subscriptionTopicRepository = new SubscriptionTopicRepository(dataSource);
        data = TestData.seed(dataSource);
    }

    @Test
    void create_whenCreated_isPresent() {
        final var subscriptionTopic = subscriptionTopicRepository.find(SUBSCRIPTION_A, PATH_A, TOPIC_A);
        final var topicId = topicRepository.find(PATH_A, TOPIC_A).orElseThrow().id();
        final var subscriptionId = subscriptionRepository.find(SUBSCRIPTION_A).orElseThrow().id();
        assertThat(subscriptionTopic).isPresent().get().extracting(SubscriptionTopic::topicId).isEqualTo(topicId);
        assertThat(subscriptionTopic).isPresent().get().extracting(SubscriptionTopic::subscriptionId).isEqualTo(subscriptionId);
    }

    @Test
    void created_whenCreatedMultipleTimes_allPresent() {
        assertThat(new SubscriptionTopicRepository(dataSource).findAll(100, 0)).hasSize(8);
    }

    @Test
    void delete_whenDeleted_isNotPresent() {
        System.out.println(subscriptionTopicRepository.findAll(100, 0).size());
        subscriptionRepository.unsubscribe(SUBSCRIPTION_A, PATH_A, TOPIC_A);
        System.out.println(subscriptionTopicRepository.findAll(100, 0).size());
        subscriptionRepository.unsubscribe(SUBSCRIPTION_A, PATH_A, TOPIC_B);
        System.out.println(subscriptionTopicRepository.findAll(100, 0).size());
        subscriptionRepository.unsubscribe(SUBSCRIPTION_C, PATH_B, TOPIC_C);
        System.out.println(subscriptionTopicRepository.findAll(100, 0).size());
        subscriptionRepository.unsubscribe(SUBSCRIPTION_C, PATH_B, TOPIC_D);
        System.out.println(subscriptionTopicRepository.findAll(100, 0).size());
        assertThat(subscriptionTopicRepository.findAll(100, 0)).hasSize(4);
    }

    @Test
    void deleteAll_whenDeleted_otherSubscriptionsUnaffected() {
        assertThat(new SubscriptionTopicRepository(dataSource).findAll(100, 0))
                .hasSize(8)
                .extracting(SubscriptionTopic::subscriptionId)
                .containsAll(data.subscriptions().stream().map(Subscription::id).toList());
        // UNSUBSCRIBE S1
        subscriptionRepository.unsubscribe(SUBSCRIPTION_A, PATH_A, TOPIC_A);
        subscriptionRepository.unsubscribe(SUBSCRIPTION_A, PATH_A, TOPIC_B);
        subscriptionRepository.unsubscribe(SUBSCRIPTION_C, PATH_B, TOPIC_C);
        subscriptionRepository.unsubscribe(SUBSCRIPTION_C, PATH_B, TOPIC_D);
        assertThat(subscriptionTopicRepository.findAll(100, 0)).hasSize(4);
    }

    @Test
    void findAll_whenLimitAndOffset_returnsCorrectItems() {
        final var s1 = subscriptionTopicRepository.find(SUBSCRIPTION_A, PATH_A, TOPIC_A).orElseThrow().id();
        final var s2 = subscriptionTopicRepository.find(SUBSCRIPTION_A, PATH_A, TOPIC_B).orElseThrow().id();
        final var s3 = subscriptionTopicRepository.find(SUBSCRIPTION_B, PATH_A, TOPIC_A).orElseThrow().id();
        final var s4 = subscriptionTopicRepository.find(SUBSCRIPTION_B, PATH_A, TOPIC_B).orElseThrow().id();
        // PAGE 1
        assertThat(new SubscriptionTopicRepository(dataSource).findAll(2, 0))
                .extracting(SubscriptionTopic::id)
                .containsExactlyInAnyOrder(s1, s2);
        // PAGE 2
        assertThat(new SubscriptionTopicRepository(dataSource).findAll(2, 2))
                .extracting(SubscriptionTopic::id)
                .containsExactlyInAnyOrder(s3, s4);

    }
}
