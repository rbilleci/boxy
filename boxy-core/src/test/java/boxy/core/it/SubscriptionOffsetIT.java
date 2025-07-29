package boxy.core.it;

import boxy.core.repository.*;
import boxy.core.model.SubscriptionOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;


import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public class SubscriptionOffsetIT extends BaseIT {

    private SubscriptionOffsetRepository subscriptionOffsetRepository;
    private TestData data;

    @BeforeEach
    void setup() {
        subscriptionOffsetRepository = new SubscriptionOffsetRepository(dataSource);
        data = TestData.seed(dataSource);
    }

    @Test
    void commit_whenOffsetDoesNotExist_insertsNewRow() {
        final var subscriptionOffset = data.subscriptionOffset();
        final var subscriptionId = subscriptionOffset.subscriptionId();
        final var partitionId = subscriptionOffset.partitionId();

        // COMMIT HWM
        subscriptionOffsetRepository.commit(subscriptionOffset.id(), 100L);
        assertThat(subscriptionOffsetRepository.find(subscriptionId, partitionId))
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
        subscriptionOffsetRepository.commit(subscriptionOffset.id(), 100L);
        subscriptionOffsetRepository.commit(subscriptionOffset.id(), 101L);
        subscriptionOffsetRepository.commit(subscriptionOffset.id(), 102L);
        assertThat(subscriptionOffsetRepository.find(subscriptionId, partitionId))
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
        subscriptionOffsetRepository.commit(subscriptionOffset.id(), 100L);
        subscriptionOffsetRepository.commit(subscriptionOffset.id(), 10L);
        assertThat(subscriptionOffsetRepository.find(subscriptionId, partitionId))
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
        subscriptionOffsetRepository.commit(subscriptionOffset.id(), 100L);
        subscriptionOffsetRepository.commit(subscriptionOffset.id(), 100L);
        assertThat(subscriptionOffsetRepository.find(subscriptionId, partitionId))
                .isPresent()
                .get()
                .extracting(SubscriptionOffset::committedOffset)
                .isEqualTo(100L);

    }
}
