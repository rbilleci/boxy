package boxy.core.it;

import boxy.core.repository.EventRepository;
import boxy.core.repository.SubscriptionTopicRepository;
import boxy.core.repository.CursorRepository;
import boxy.core.domain.SubscriptionTopic;
import boxy.core.domain.Cursor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import static boxy.core.it.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public class ProcessorIT extends BaseIT {

    private static final String DATA = "{\"key\": \"value\"}\n";

    private SubscriptionTopicRepository subscriptionTopicRepository;
    private CursorRepository cursorRepository;
    private EventRepository eventRepository;
    private TestData data;

    @BeforeEach
    void setup() {
        subscriptionTopicRepository = new SubscriptionTopicRepository(dataSource);
        cursorRepository = new CursorRepository(dataSource);
        eventRepository = new EventRepository(dataSource);
        data = TestData.seed(dataSource);
    }

    @Test
    void leasesAvailable_whenViewHasOneItem_returnsListWithOneItem() {
        final var subscriptionId = subscriptionTopicRepository.find(TENANT_1, SUBSCRIPTION_A, NAMESPACE_A, TOPIC_A).orElseThrow().id();
        // PUBLISH
        eventRepository.publish(TENANT_1, NAMESPACE_A, TOPIC_A, "partitionKey", DATA);
        // VALIDATE
        final var leasable = cursorRepository.findLeasable(subscriptionId, 100, 0);
        assertThat(leasable).hasSize(1);
        final var result = leasable.getFirst();
        assertThat(result.subscriptionId()).isEqualTo(subscriptionId);
        assertThat(result.position()).isEqualTo(0L);
    }

    @Test
    void leasesAvailable_whenViewHasMultipleItems_returnsAllItems() {
        // PUBLISH
        eventRepository.publish(TENANT_1, NAMESPACE_A, TOPIC_A, "pk1", DATA);
        eventRepository.publish(TENANT_1, NAMESPACE_A, TOPIC_A, "pk2", DATA);
        eventRepository.publish(TENANT_1, NAMESPACE_A, TOPIC_B, "pk3", DATA);
        eventRepository.publish(TENANT_1, NAMESPACE_A, TOPIC_B, "pk4", DATA);
        eventRepository.publish(TENANT_2, NAMESPACE_B, TOPIC_C, "pk5", DATA);
        eventRepository.publish(TENANT_2, NAMESPACE_B, TOPIC_C, "pk6", DATA);
        eventRepository.publish(TENANT_2, NAMESPACE_B, TOPIC_D, "pk7", DATA);
        eventRepository.publish(TENANT_2, NAMESPACE_B, TOPIC_D, "pk8", DATA);
        // VALIDATE
        final var leasable = cursorRepository.findLeasable(100, 0);
        assertThat(leasable)
                .hasSize(16)
                .extracting(Cursor::subscriptionId)
                .containsAll(data.subscriptionTopics().stream().map(SubscriptionTopic::subscriptionId).toList());
    }
}
