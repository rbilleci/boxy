package boxy.core.it;

import boxy.core.dao.EventDao;
import boxy.core.dao.SubscriptionDao;
import boxy.core.dao.SubscriptionOffsetDao;
import boxy.core.model.Subscription;
import boxy.core.model.SubscriptionOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import static boxy.core.it.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public class ProcessorIT extends BaseIT {

    private static final String DATA = "{\"key\": \"value\"}\n";

    private SubscriptionDao subscriptionDao;
    private SubscriptionOffsetDao subscriptionOffsetDao;
    private EventDao eventDao;
    private TestData data;

    @BeforeEach
    void setup() {
        subscriptionDao = new SubscriptionDao(dataSource);
        subscriptionOffsetDao = new SubscriptionOffsetDao(dataSource);
        eventDao = new EventDao(dataSource);
        data = TestData.seed(dataSource);
    }

    @Test
    void leasesAvailable_whenViewHasOneItem_returnsListWithOneItem() {
        final var subscriptionId = subscriptionDao.find(TENANT_1, CONSUMER_GROUP_A, TOPIC_A).orElseThrow().id();
        // PUBLISH
        eventDao.publish(TENANT_1, TOPIC_A, "partitionKey", DATA);
        // VALIDATE
        final var subscriptionOffsets = subscriptionOffsetDao.findLeasable(subscriptionId, 100, 0);
        assertThat(subscriptionOffsets).hasSize(1);
        final var result = subscriptionOffsets.getFirst();
        assertThat(result.subscriptionId()).isEqualTo(subscriptionId);
        assertThat(result.committedOffset()).isEqualTo(0L);
    }

    @Test
    void leasesAvailable_whenViewHasMultipleItems_returnsAllItems() {
        // PUBLISH
        eventDao.publish(TENANT_1, TOPIC_A, "pk1", DATA);
        eventDao.publish(TENANT_1, TOPIC_A, "pk2", DATA);
        eventDao.publish(TENANT_1, TOPIC_B, "pk3", DATA);
        eventDao.publish(TENANT_1, TOPIC_B, "pk4", DATA);
        eventDao.publish(TENANT_2, TOPIC_C, "pk5", DATA);
        eventDao.publish(TENANT_2, TOPIC_C, "pk6", DATA);
        eventDao.publish(TENANT_2, TOPIC_D, "pk7", DATA);
        eventDao.publish(TENANT_2, TOPIC_D, "pk8", DATA);
        // VALIDATE
        final var leasable = subscriptionOffsetDao.findLeasable(100, 0);
        assertThat(leasable)
                .hasSize(16)
                .extracting(SubscriptionOffset::subscriptionId)
                .containsAll(data.subscriptions().stream().map(Subscription::id).toList());
    }
}
