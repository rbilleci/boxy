package boxy.core.it;

import boxy.core.repository.*;
import boxy.core.domain.Cursor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;


import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public class CursorIT extends BaseIT {

    private CursorRepository cursorRepository;
    private TestData data;

    @BeforeEach
    void setup() {
        cursorRepository = new CursorRepository(dataSource);
        data = TestData.seed(dataSource);
    }

    @Test
    void commit_whenPositionDoesNotExist_insertsNewRow() {
        final var cursor = data.cursor();
        final var subscriptionId = data.subscriptions().getFirst().id();
        final var partitionId = cursor.partitionId();

        // COMMIT HWM
        cursorRepository.commit(cursor.id(), 100L);
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
        cursorRepository.commit(cursor.id(), 100L);
        cursorRepository.commit(cursor.id(), 101L);
        cursorRepository.commit(cursor.id(), 102L);
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
        cursorRepository.commit(cursor.id(), 100L);
        cursorRepository.commit(cursor.id(), 10L);
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
        cursorRepository.commit(cursor.id(), 100L);
        cursorRepository.commit(cursor.id(), 100L);
        assertThat(cursorRepository.find(subscriptionId, partitionId))
                .isPresent()
                .get()
                .extracting(Cursor::position)
                .isEqualTo(100L);

    }
}
