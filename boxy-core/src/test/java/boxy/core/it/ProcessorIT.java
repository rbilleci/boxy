package boxy.core.it;

import boxy.core.repository.EventRepository;
import boxy.core.repository.CursorRepository;
import boxy.core.repository.SubscriptionTopicRepository;
import boxy.core.domain.Cursor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import static boxy.core.it.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public class ProcessorIT extends BaseIT {

    private static final String DATA = "{\"key\": \"value\"}\n";

    private CursorRepository cursorRepository;
    private EventRepository eventRepository;
    private SubscriptionTopicRepository subscriptionTopicRepository;
    private TestData data;

    @BeforeEach
    void setup() {
        cursorRepository = new CursorRepository(dataSource);
        eventRepository = new EventRepository(dataSource);
        subscriptionTopicRepository = new SubscriptionTopicRepository(dataSource);
        data = TestData.seed(dataSource);
    }

    @Test
    void leasesAvailable_whenViewHasOneItem_returnsListWithOneItem() {
        final var subscriptionId = data.subscriptions().getFirst().id();
        // PUBLISH
        eventRepository.publish(PATH_A, TOPIC_A, "partitionKey", DATA);
        // VALIDATE
        final var leasable = cursorRepository.findLeasable(subscriptionId, 100, 0);
        assertThat(leasable).hasSize(1);
        final var result = leasable.getFirst();
        final var subscription = subscriptionTopicRepository.find(SUBSCRIPTION_A, PATH_A, TOPIC_A).orElseThrow();
        assertThat(result.subscriptionTopicId()).isEqualTo(subscription.id());
        assertThat(result.position()).isEqualTo(0L);
    }

    @Test
    void leasesAvailable_whenViewHasMultipleItems_returnsAllItems() {
        // PUBLISH
        eventRepository.publish(PATH_A, TOPIC_A, "pk1", DATA);
        eventRepository.publish(PATH_A, TOPIC_A, "pk2", DATA);
        eventRepository.publish(PATH_A, TOPIC_B, "pk3", DATA);
        eventRepository.publish(PATH_A, TOPIC_B, "pk4", DATA);
        eventRepository.publish(PATH_B, TOPIC_C, "pk5", DATA);
        eventRepository.publish(PATH_B, TOPIC_C, "pk6", DATA);
        eventRepository.publish(PATH_B, TOPIC_D, "pk7", DATA);
        eventRepository.publish(PATH_B, TOPIC_D, "pk8", DATA);
        // VALIDATE
        final var leasable = cursorRepository.findLeasable(100, 0);
        assertThat(leasable).hasSize(16);
    }
}
