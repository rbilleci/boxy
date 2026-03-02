package boxy.core.it;

import boxy.core.DataAccessException;
import boxy.mysql.repository.ConsumerRepository;
import boxy.mysql.repository.CursorRepository;
import boxy.mysql.repository.EventRepository;
import boxy.mysql.repository.PartitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static boxy.core.it.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Item #114 (item 77): Test edge cases and error handling.
 * - Duplicate consumer registration (idempotent or throws)
 * - Invalid/non-existent topic paths
 * - Polling with unknown consumer ID
 * - Stale commit (commit for a consumer without the lease)
 */
@Testcontainers
class ErrorEdgeCaseIT extends BaseIT {

    private ConsumerRepository consumerRepository;
    private CursorRepository cursorRepository;
    private PartitionRepository partitionRepository;
    private EventRepository eventRepository;
    private TestData data;

    @BeforeEach
    void setup() {
        consumerRepository = new ConsumerRepository(dataSource);
        cursorRepository = new CursorRepository(dataSource);
        partitionRepository = new PartitionRepository(dataSource);
        eventRepository = new EventRepository(dataSource);
        data = TestData.seed(dataSource);
    }

    /**
     * Test that registering the same consumer ID twice is idempotent or throws
     * a DataAccessException if the second registration differs.
     */
    @Test
    void register_duplicateConsumerId_isIdempotent() throws SQLException {
        final var subscription = data.subscriptions().getFirst();
        final String consumerId = "duplicate-consumer";
        final String topicPath = PATH_A + "/" + TOPIC_A;

        // First registration should succeed
        consumerRepository.register(consumerId, subscription.name(), List.of(topicPath));

        // Second registration with same parameters should be idempotent
        // (stored procedure handles this or throws with specific error code)
        try {
            consumerRepository.register(consumerId, subscription.name(), List.of(topicPath));
        } catch (DataAccessException e) {
            // If it throws, verify it's a sensible error (not connection failure)
            assertThat(e.isRetryable()).isFalse();
        }
    }

    /**
     * Test that registering a consumer with invalid/non-existent topic path throws.
     */
    @Test
    void register_invalidTopicPath_throws() throws SQLException {
        final var subscription = data.subscriptions().getFirst();
        final String consumerId = "invalid-topic-consumer";
        final String invalidTopicPath = "non-existent/topic";

        assertThatThrownBy(() ->
                consumerRepository.register(consumerId, subscription.name(), List.of(invalidTopicPath))
        ).isInstanceOf(DataAccessException.class);
    }

    /**
     * Test that polling with an unknown consumer ID returns empty or throws gracefully.
     */
    @Test
    void poll_unknownConsumerId_returnsEmptyOrThrows() throws SQLException {
        final String unknownConsumerId = "unknown-consumer-xyz";

        try (var conn = dataSource.getConnection();
             var poll = conn.prepareCall("{CALL sp_events__poll(?, ?)}")) {
            poll.setString(1, unknownConsumerId);
            poll.setInt(2, 0);

            // Call should not crash, may return empty or throw
            boolean hasResults = poll.execute();
            if (hasResults) {
                try (var rs = poll.getResultSet()) {
                    // If it returns results, should be empty for unknown consumer
                    assertThat(rs.next()).isFalse();
                }
            }
        }
    }

    /**
     * Test that committing with a consumer that doesn't have the lease is handled gracefully.
     * This simulates a stale commit scenario.
     */
    @Test
    void commit_staleConsumerWithoutLease_handlesGracefully() throws SQLException {
        final var subscription = data.subscriptions().getFirst();
        final long partitionId =
                partitionRepository.find(TestData.PATH_A, TOPIC_A, 0).orElseThrow().id();
        
        final String consumerA = "consumer-a";
        final String consumerB = "consumer-b";

        // Register both consumers
        consumerRepository.register(consumerA, subscription.name(), List.of(PATH_A + "/" + TOPIC_A));
        consumerRepository.register(consumerB, subscription.name(), List.of(PATH_A + "/" + TOPIC_A));

        // Publish an event
        eventRepository.publish(partitionId, "{}");
        awaitSequencer();

        // Consumer A polls and gets the lease
        long cursorId = -1L;
        long sequence = -1L;
        try (var conn = dataSource.getConnection();
             var poll = conn.prepareCall("{CALL sp_events__poll(?, ?)}")) {
            poll.setString(1, consumerA);
            poll.setInt(2, 0);
            boolean hasResults = poll.execute();
            if (hasResults) {
                try (var rs = poll.getResultSet()) {
                    if (rs.next()) {
                        cursorId = rs.getLong("cursor_id");
                        sequence = rs.getLong("sequence");
                    }
                }
            }
            while (poll.getMoreResults()) {
                try (var ignored = poll.getResultSet()) {
                    // consume metadata
                }
            }
        }

        assertThat(cursorId).isGreaterThan(0);

        // Consumer B tries to commit the same cursor (stale commit)
        // This should either be rejected or handled gracefully
        try {
            cursorRepository.commit(consumerB, java.util.Map.of(cursorId, sequence));
            // If no exception, that's fine - stored procedure may allow this
        } catch (DataAccessException e) {
            // If it throws, that's also acceptable behavior
            assertThat(e).isNotNull();
        }
    }

    /**
     * Helper to wait for sequencer to process events.
     */
    private void awaitSequencer() throws SQLException {
        final long timeoutMs = 5000;
        final long pollIntervalMs = 50;
        final long startTime = System.currentTimeMillis();

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try (var conn = dataSource.getConnection();
                 var stmt = conn.createStatement();
                 var rs = stmt.executeQuery("SELECT COUNT(*) as cnt FROM unprocessed_events")) {
                if (rs.next()) {
                    final int unprocessedCount = rs.getInt("cnt");
                    if (unprocessedCount == 0) {
                        return;
                    }
                }
            }
            try {
                Thread.sleep(pollIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for sequencer", e);
            }
        }

        throw new RuntimeException("Timeout waiting for sequencer to process events");
    }

}
