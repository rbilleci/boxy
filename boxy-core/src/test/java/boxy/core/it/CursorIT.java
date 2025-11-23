package boxy.core.it;

import boxy.core.domain.Cursor;
import boxy.core.repository.ConsumerRepository;
import boxy.core.repository.CursorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static boxy.core.it.TestData.PATH_A;
import static boxy.core.it.TestData.TOPIC_A;
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
        sessionId = "cursor-test-session";
        consumerRepository.register(sessionId, data.subscriptions().getFirst().name(), List.of(PATH_A + "/" + TOPIC_A));
    }

    @Test
    void commit_whenPositionDoesNotExist_insertsNewRow() {
        final var cursor = data.cursor();
        final var subscriptionId = data.subscriptions().getFirst().id();
        final var partitionId = cursor.partitionId();

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

        cursorRepository.commit(sessionId, Map.of(cursor.id(), 100L));
        cursorRepository.commit(sessionId, Map.of(cursor.id(), 100L));
        assertThat(cursorRepository.find(subscriptionId, partitionId))
                .isPresent()
                .get()
                .extracting(Cursor::position)
                .isEqualTo(100L);

    }
}
