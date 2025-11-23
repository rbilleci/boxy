package boxy.core.it;

import boxy.core.repository.*;
import boxy.core.domain.Cursor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;


import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public class CursorIT extends BaseIT {

    private CursorRepository cursorRepository;
    private ConsumerRepository consumerRepository;
    private TestData data;
    private String sessionId;

    @BeforeEach
    void setup() {
        cursorRepository = new CursorRepository(dataSource);
        consumerRepository = new ConsumerRepository(dataSource);
        data = TestData.seed(dataSource);
        sessionId = "cursor-consumer";
        consumerRepository.register(sessionId, TestData.SUBSCRIPTION_A, List.of(TestData.PATH_A + "/" + TestData.TOPIC_A));
    }

    @Test
    void commit_whenPositionDoesNotExist_insertsNewRow() {
        final var cursor = data.cursor();
        final var subscriptionId = data.subscriptions().getFirst().id();
        final var partitionId = cursor.partitionId();

        // COMMIT HWM
        cursorRepository.commit(sessionId, Map.of(cursor.id(), 100L));
        assertThat(cursorRepository.find(subscriptionId, partitionId))
                .isPresent()
                .get()
                .extracting(Cursor::position)
                .isEqualTo(100L);
    }

    @Test
    void commit_whenPositionIsGreater_updatesExistingRow() {
        final var cursor = data.cursor();
        final var subscriptionId = data.subscriptions().getFirst().id();
        final var partitionId = cursor.partitionId();

        // COMMIT HWM
        cursorRepository.commit(sessionId, Map.of(cursor.id(), 100L));
        cursorRepository.commit(sessionId, Map.of(cursor.id(), 101L));
        cursorRepository.commit(sessionId, Map.of(cursor.id(), 102L));
        assertThat(cursorRepository.find(subscriptionId, partitionId))
                .isPresent()
                .get()
                .extracting(Cursor::position)
                .isEqualTo(102L);
    }

    @Test
    void commit_whenPositionIsLower_doesNotUpdateExistingRow() {
        final var cursor = data.cursor();
        final var subscriptionId = data.subscriptions().getFirst().id();
        final var partitionId = cursor.partitionId();

        // COMMIT HWM
        cursorRepository.commit(sessionId, Map.of(cursor.id(), 100L));
        cursorRepository.commit(sessionId, Map.of(cursor.id(), 10L));
        assertThat(cursorRepository.find(subscriptionId, partitionId))
                .isPresent()
                .get()
                .extracting(Cursor::position)
                .isEqualTo(100L);
    }

    @Test
    void commit_whenPositionIsEqual_doesNotUpdateExistingRow() {
        final var cursor = data.cursor();
        final var subscriptionId = data.subscriptions().getFirst().id();
        final var partitionId = cursor.partitionId();

        // COMMIT HWM
        cursorRepository.commit(sessionId, Map.of(cursor.id(), 100L));
        cursorRepository.commit(sessionId, Map.of(cursor.id(), 100L));
        assertThat(cursorRepository.find(subscriptionId, partitionId))
                .isPresent()
                .get()
                .extracting(Cursor::position)
                .isEqualTo(100L);

    }
}
