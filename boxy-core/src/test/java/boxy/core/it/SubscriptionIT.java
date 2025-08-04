package boxy.core.it;

import boxy.core.domain.ConsumerGroup;
import boxy.core.repository.ConsumerGroupRepository;
import boxy.core.repository.SubscriptionRepository;
import boxy.core.repository.TopicRepository;
import boxy.core.domain.Subscription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import static boxy.core.it.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public class SubscriptionIT extends BaseIT {

    private ConsumerGroupRepository consumerGroupRepository;
    private SubscriptionRepository subscriptionRepository;
    private TopicRepository topicRepository;
    private TestData data;

    @BeforeEach
    void setup() {
        consumerGroupRepository = new ConsumerGroupRepository(dataSource);
        topicRepository = new TopicRepository(dataSource);
        subscriptionRepository = new SubscriptionRepository(dataSource);
        data = TestData.seed(dataSource);
    }

    @Test
    void create_whenCreated_isPresent() {
        final var subscription = subscriptionRepository.find(CONSUMER_GROUP_A, PATH_A, TOPIC_A);
        final var topicId = topicRepository.find(PATH_A, TOPIC_A).orElseThrow().id();
        final var consumerGroupId = consumerGroupRepository.find(CONSUMER_GROUP_A).orElseThrow().id();
        assertThat(subscription).isPresent().get().extracting(Subscription::topicId).isEqualTo(topicId);
        assertThat(subscription).isPresent().get().extracting(Subscription::consumerGroupId).isEqualTo(consumerGroupId);
    }

    @Test
    void created_whenCreatedMultipleTimes_allPresent() {
        assertThat(new SubscriptionRepository(dataSource).findAll(100, 0)).hasSize(8);
    }

    @Test
    void delete_whenDeleted_isNotPresent() {
        System.out.println(subscriptionRepository.findAll(100, 0).size());
        consumerGroupRepository.unsubscribe(CONSUMER_GROUP_A, PATH_A, TOPIC_A);
        System.out.println(subscriptionRepository.findAll(100, 0).size());
        consumerGroupRepository.unsubscribe(CONSUMER_GROUP_A, PATH_A, TOPIC_B);
        System.out.println(subscriptionRepository.findAll(100, 0).size());
        consumerGroupRepository.unsubscribe(CONSUMER_GROUP_C, PATH_B, TOPIC_C);
        System.out.println(subscriptionRepository.findAll(100, 0).size());
        consumerGroupRepository.unsubscribe(CONSUMER_GROUP_C, PATH_B, TOPIC_D);
        System.out.println(subscriptionRepository.findAll(100, 0).size());
        assertThat(subscriptionRepository.findAll(100, 0)).hasSize(4);
    }

    @Test
    void deleteAll_whenDeleted_otherConsumerGroupsUnaffected() {
        assertThat(new SubscriptionRepository(dataSource).findAll(100, 0))
                .hasSize(8)
                .extracting(Subscription::consumerGroupId)
                .containsAll(data.consumerGroups().stream().map(ConsumerGroup::id).toList());
        // UNSUBSCRIBE S1
        consumerGroupRepository.unsubscribe(CONSUMER_GROUP_A, PATH_A, TOPIC_A);
        consumerGroupRepository.unsubscribe(CONSUMER_GROUP_A, PATH_A, TOPIC_B);
        consumerGroupRepository.unsubscribe(CONSUMER_GROUP_C, PATH_B, TOPIC_C);
        consumerGroupRepository.unsubscribe(CONSUMER_GROUP_C, PATH_B, TOPIC_D);
        assertThat(subscriptionRepository.findAll(100, 0)).hasSize(4);
    }

    @Test
    void findAll_whenLimitAndOffset_returnsCorrectItems() {
        final var s1 = subscriptionRepository.find(CONSUMER_GROUP_A, PATH_A, TOPIC_A).orElseThrow().id();
        final var s2 = subscriptionRepository.find(CONSUMER_GROUP_A, PATH_A, TOPIC_B).orElseThrow().id();
        final var s3 = subscriptionRepository.find(CONSUMER_GROUP_B, PATH_A, TOPIC_A).orElseThrow().id();
        final var s4 = subscriptionRepository.find(CONSUMER_GROUP_B, PATH_A, TOPIC_B).orElseThrow().id();
        // PAGE 1
        assertThat(new SubscriptionRepository(dataSource).findAll(2, 0))
                .extracting(Subscription::id)
                .containsExactlyInAnyOrder(s1, s2);
        // PAGE 2
        assertThat(new SubscriptionRepository(dataSource).findAll(2, 2))
                .extracting(Subscription::id)
                .containsExactlyInAnyOrder(s3, s4);

    }
}
