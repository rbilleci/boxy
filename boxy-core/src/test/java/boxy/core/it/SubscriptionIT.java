package boxy.core.it;

import boxy.core.repository.SubscriptionRepository;
import boxy.core.domain.Subscription;
import boxy.core.repository.ConsumerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import static boxy.core.it.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public class SubscriptionIT extends BaseIT {

    private SubscriptionRepository subscriptionRepository;
    private ConsumerRepository consumerRepository;
    private TestData data;

    @BeforeEach
    void setup() {
        subscriptionRepository = new SubscriptionRepository(dataSource);
        consumerRepository = new ConsumerRepository(dataSource);
        data = TestData.seed(dataSource);
    }

    @Test
    void create_whenCreated_isPresent() {
        final var subscription = subscriptionRepository.find(SUBSCRIPTION_A).orElseThrow();
        assertThat(subscription).extracting(Subscription::name).isEqualTo(SUBSCRIPTION_A);
    }

    @Test
    void deleted_whenDeleted_isNotPresent() {
        subscriptionRepository.find(SUBSCRIPTION_A).orElseThrow();
        consumerRepository.findAll(100, 0).forEach(c -> consumerRepository.delete(c.id()));
        subscriptionRepository.delete(SUBSCRIPTION_A);
        assertThat(subscriptionRepository.find(SUBSCRIPTION_A)).isNotPresent();
    }

    @Test
    void find_whenNotCreated_isNotPresent() {
        assertThat(subscriptionRepository.find("UNDEFINED")).isNotPresent();
    }

    @Test
    void findAll_whenPaging_returnsCorrectItems() {
        assertThat(subscriptionRepository.findAll(100, 0))
                .hasSize(data.subscriptions().size())
                .extracting(Subscription::name)
                .containsExactlyInAnyOrder(
                        SUBSCRIPTION_A, SUBSCRIPTION_B, SUBSCRIPTION_C, SUBSCRIPTION_D);
    }

    @Test
    void findAll_whenEmpty() {
        consumerRepository.findAll(100, 0).forEach(c -> consumerRepository.delete(c.id()));
        subscriptionRepository.findAll(100, 0).forEach(s -> subscriptionRepository.delete(s.name()));
        assertThat(subscriptionRepository.findAll(100, 0)).isEmpty();
}
}
