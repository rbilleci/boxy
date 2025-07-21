package boxy.persistence.it;

import boxy.persistence.dao.*;
import boxy.persistence.model.SubscriptionOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;


import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public class SubscriptionOffsetIT extends BaseIT {

    private SubscriptionOffsetDao subscriptionOffsetDao;
    private TestData data;

    @BeforeEach
    void setup() {
        subscriptionOffsetDao = jdbi.onDemand(SubscriptionOffsetDao.class);
        data = TestData.seed(jdbi);
    }

    @Test
    void commit_whenOffsetDoesNotExist_insertsNewRow() {
        final var subscriptionOffset = data.subscriptionOffset();
        final var subscriptionId = subscriptionOffset.subscriptionId();
        final var partitionId = subscriptionOffset.partitionId();

        // COMMIT HWM
        subscriptionOffsetDao.commit(subscriptionOffset.id(), 100L);
        assertThat(subscriptionOffsetDao.find(subscriptionId, partitionId))
                .isPresent()
                .get()
                .extracting(SubscriptionOffset::committedOffset)
                .isEqualTo(100L);
    }

    @Test
    void commit_whenOffsetIsHigher_updatesExistingRow() {
        final var subscriptionOffset = data.subscriptionOffset();
        final var subscriptionId = subscriptionOffset.subscriptionId();
        final var partitionId = subscriptionOffset.partitionId();

        // COMMIT HWM
        subscriptionOffsetDao.commit(subscriptionOffset.id(), 100L);
        subscriptionOffsetDao.commit(subscriptionOffset.id(), 101L);
        subscriptionOffsetDao.commit(subscriptionOffset.id(), 102L);
        assertThat(subscriptionOffsetDao.find(subscriptionId, partitionId))
                .isPresent()
                .get()
                .extracting(SubscriptionOffset::committedOffset)
                .isEqualTo(102L);
    }

    @Test
    void commit_whenOffsetIsLower_doesNotUpdateExistingRow() {
        final var subscriptionOffset = data.subscriptionOffset();
        final var subscriptionId = subscriptionOffset.subscriptionId();
        final var partitionId = subscriptionOffset.partitionId();

        // COMMIT HWM
        subscriptionOffsetDao.commit(subscriptionOffset.id(), 100L);
        subscriptionOffsetDao.commit(subscriptionOffset.id(), 10L);
        assertThat(subscriptionOffsetDao.find(subscriptionId, partitionId))
                .isPresent()
                .get()
                .extracting(SubscriptionOffset::committedOffset)
                .isEqualTo(100L);
    }

    @Test
    void commit_whenOffsetIsEqual_doesNotUpdateExistingRow() {
        final var subscriptionOffset = data.subscriptionOffset();
        final var subscriptionId = subscriptionOffset.subscriptionId();
        final var partitionId = subscriptionOffset.partitionId();

        // COMMIT HWM
        subscriptionOffsetDao.commit(subscriptionOffset.id(), 100L);
        subscriptionOffsetDao.commit(subscriptionOffset.id(), 100L);
        assertThat(subscriptionOffsetDao.find(subscriptionId, partitionId))
                .isPresent()
                .get()
                .extracting(SubscriptionOffset::committedOffset)
                .isEqualTo(100L);

    }
}
