package boxy.core.it;

import boxy.core.repository.EventRepository;
import boxy.core.repository.SubscriptionRepository;
import boxy.core.repository.SubscriptionOffsetRepository;
import boxy.core.domain.Subscription;
import boxy.core.domain.SubscriptionOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import static boxy.core.it.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public class ProcessorIT extends BaseIT {

    private static final String DATA = "{\"key\": \"value\"}\n";

    private SubscriptionRepository subscriptionRepository;
    private SubscriptionOffsetRepository subscriptionOffsetRepository;
    private EventRepository eventRepository;
    private TestData data;

    @BeforeEach
    void setup() {
        subscriptionRepository = new SubscriptionRepository(dataSource);
        subscriptionOffsetRepository = new SubscriptionOffsetRepository(dataSource);
        eventRepository = new EventRepository(dataSource);
        data = TestData.seed(dataSource);
    }

    @Test
    void leasesAvailable_whenViewHasOneItem_returnsListWithOneItem() {
        final var subscriptionId = subscriptionRepository.find(TENANT_1, CONSUMER_GROUP_A, TOPIC_A).orElseThrow().id();
        // PUBLISH
        eventRepository.publish(TENANT_1, TOPIC_A, "partitionKey", DATA);
        // VALIDATE
        final var leasable = subscriptionOffsetRepository.findLeasable(subscriptionId, 100, 0);
        assertThat(leasable).hasSize(1);
        final var result = leasable.getFirst();
        assertThat(result.subscriptionId()).isEqualTo(subscriptionId);
        assertThat(result.committedOffset()).isEqualTo(0L);
    }

    @Test
    void leasesAvailable_whenViewHasMultipleItems_returnsAllItems() {
        // PUBLISH
        eventRepository.publish(TENANT_1, TOPIC_A, "pk1", DATA);
        eventRepository.publish(TENANT_1, TOPIC_A, "pk2", DATA);
        eventRepository.publish(TENANT_1, TOPIC_B, "pk3", DATA);
        eventRepository.publish(TENANT_1, TOPIC_B, "pk4", DATA);
        eventRepository.publish(TENANT_2, TOPIC_C, "pk5", DATA);
        eventRepository.publish(TENANT_2, TOPIC_C, "pk6", DATA);
        eventRepository.publish(TENANT_2, TOPIC_D, "pk7", DATA);
        eventRepository.publish(TENANT_2, TOPIC_D, "pk8", DATA);
        // VALIDATE
        final var leasable = subscriptionOffsetRepository.findLeasable(100, 0);
        assertThat(leasable)
                .hasSize(16)
                .extracting(SubscriptionOffset::subscriptionId)
                .containsAll(data.subscriptions().stream().map(Subscription::id).toList());
    }
}
