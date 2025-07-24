package boxy.core.it;

import boxy.core.dao.LeaseDao;
import boxy.core.service.EventService;
import boxy.core.service.SubscriptionService;
import boxy.core.model.Subscription;
import boxy.core.model.SubscriptionOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import static boxy.core.it.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public class ProcessorIT extends BaseIT {

    private static final String DATA = """
            {"key": "value"}
            """;

    private SubscriptionService subscriptionService;
    private EventService eventService;
    private LeaseDao leaseDao;
    private TestData data;

    @BeforeEach
    void setup() {
        subscriptionService = new SubscriptionService(dataSource);
        eventService = new EventService(dataSource);
        leaseDao = new LeaseDao(dataSource);
        data = TestData.seed(dataSource);
    }

    @Test
    void leasesAvailable_whenViewHasOneItem_returnsListWithOneItem() {
        final var subscriptionId = subscriptionService.find(TENANT_1, CONSUMER_GROUP_A, TOPIC_A).orElseThrow().id();
        // PUBLISH
        eventService.publish(TENANT_1, TOPIC_A, "partitionKey", DATA);
        // VALIDATE
        final var subscriptionOffsets = leaseDao.leasesAvailable(100, 0);
        assertThat(subscriptionOffsets).hasSize(2);
        final var result = subscriptionOffsets.getFirst();
        assertThat(result.subscriptionId()).isEqualTo(subscriptionId);
        assertThat(result.committedOffset()).isEqualTo(0L);
    }

    @Test
    void leasesAvailable_whenViewHasMultipleItems_returnsAllItems() {
        // PUBLISH
        eventService.publish(TENANT_1, TOPIC_A, "pk1", DATA);
        eventService.publish(TENANT_1, TOPIC_A, "pk2", DATA);
        eventService.publish(TENANT_1, TOPIC_B, "pk3", DATA);
        eventService.publish(TENANT_1, TOPIC_B, "pk4", DATA);
        eventService.publish(TENANT_2, TOPIC_C, "pk5", DATA);
        eventService.publish(TENANT_2, TOPIC_C, "pk6", DATA);
        eventService.publish(TENANT_2, TOPIC_D, "pk7", DATA);
        eventService.publish(TENANT_2, TOPIC_D, "pk8", DATA);
        // VALIDATE
        final var leasable = leaseDao.leasesAvailable(100, 0);
        assertThat(leasable)
                .hasSize(16)
                .extracting(SubscriptionOffset::subscriptionId)
                .containsAll(data.subscriptions().stream().map(Subscription::id).toList());
    }

    @Test
    void leasesAvailable_whenViewIsEmpty_returnsEmptyList() {
        assertThat(leaseDao.leasesAvailable(100, 0))
                .isNotNull()
                .isEmpty();
    }

}
