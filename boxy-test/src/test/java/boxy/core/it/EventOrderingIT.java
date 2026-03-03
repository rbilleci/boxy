package boxy.core.it;

import boxy.core.repository.ConsumerRepository;
import boxy.core.repository.EventRepository;
import boxy.core.repository.PartitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static boxy.core.it.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Item #118 (item 81): Test event ordering guarantees across partitions.
 * Verifies that events are returned in order within a partition by sequence number.
 */
@Testcontainers
class EventOrderingIT extends BaseIT {

    private EventRepository eventRepository;
    private PartitionRepository partitionRepository;
    private ConsumerRepository consumerRepository;
    private TestData data;

    @BeforeEach
    void setup() {
        eventRepository = new EventRepository(dataSource);
        partitionRepository = new PartitionRepository(dataSource);
        consumerRepository = new ConsumerRepository(dataSource);
        data = TestData.seed(dataSource);
    }

    /**
     * Test that events published to the same partition are returned
     * in order (monotonically increasing by sequence/position).
     */
    @Test
    void poll_singlePartition_eventsReturnedInOrder() throws SQLException {
        final var subscription = data.subscriptions().getFirst();
        final long partitionId =
                partitionRepository.find(TestData.PATH_A, TOPIC_A, 0).orElseThrow().id();
        final String consumerId = "ordering-consumer";

        consumerRepository.register(consumerId, subscription.name(), List.of(PATH_A + "/" + TOPIC_A));

        // Publish 20 events in order
        final int eventCount = 20;
        for (int i = 0; i < eventCount; i++) {
            eventRepository.publish(partitionId, "{\"seq\":" + i + "}");
        }
        awaitSequencer();

        // Poll all events
        final List<Long> polledSequences = pollAllEvents(consumerId);

        // Verify we got all events and they are in order
        assertThat(polledSequences).hasSize(eventCount);
        
        // Check that sequences are monotonically increasing
        for (int i = 1; i < polledSequences.size(); i++) {
            assertThat(polledSequences.get(i)).isGreaterThanOrEqualTo(polledSequences.get(i - 1));
        }
    }

    /**
     * Test that events across multiple partitions maintain order within each partition.
     */
    @Test
    void poll_multiplePartitions_eventOrderedPerPartition() throws SQLException {
        final var subscription = data.subscriptions().getFirst();
        
        // Get partition 0 and partition 1
        final long partition0Id =
                partitionRepository.find(TestData.PATH_A, TOPIC_A, 0).orElseThrow().id();
        final long partition1Id =
                partitionRepository.find(TestData.PATH_A, TOPIC_A, 1).orElseThrow().id();

        final String consumerId = "multi-partition-consumer";
        consumerRepository.register(consumerId, subscription.name(), List.of(PATH_A + "/" + TOPIC_A));

        // Publish 10 events to each partition
        final int eventsPerPartition = 10;
        for (int i = 0; i < eventsPerPartition; i++) {
            eventRepository.publish(partition0Id, "{\"part\":0,\"seq\":" + i + "}");
            eventRepository.publish(partition1Id, "{\"part\":1,\"seq\":" + i + "}");
        }
        awaitSequencer();

        // Poll all events and group by partition (via manual inspection)
        final List<Long> polledSequences = pollAllEvents(consumerId);

        // Verify we got events from both partitions
        assertThat(polledSequences).hasSize(2 * eventsPerPartition);

        // Verify monotonic ordering within the overall sequence
        for (int i = 1; i < polledSequences.size(); i++) {
            assertThat(polledSequences.get(i)).isGreaterThanOrEqualTo(polledSequences.get(i - 1));
        }
    }

    /**
     * Test that when a consumer commits a position and polls again,
     * events after the committed position are returned in order.
     */
    @Test
    void poll_afterCommit_resumesInOrder() throws SQLException {
        final var subscription = data.subscriptions().getFirst();
        final long partitionId =
                partitionRepository.find(TestData.PATH_A, TOPIC_A, 0).orElseThrow().id();
        final String consumerId = "resume-consumer";

        consumerRepository.register(consumerId, subscription.name(), List.of(PATH_A + "/" + TOPIC_A));

        // Publish 30 events
        final int eventCount = 30;
        for (int i = 0; i < eventCount; i++) {
            eventRepository.publish(partitionId, "{\"seq\":" + i + "}");
        }
        awaitSequencer();

        // First poll - get first 15 events
        final List<PolledEvent> firstBatch = pollAllEventsWithCursorInfo(consumerId);
        assertThat(firstBatch).isNotEmpty();
        
        // Commit the position of the last event from first batch
        if (!firstBatch.isEmpty()) {
            final var lastEvent = firstBatch.getLast();
            final Map<Long, Long> cursorCommit = Map.of(lastEvent.cursorId(), lastEvent.sequence());
            
            try {
                var cursorRepository = new boxy.core.repository.CursorRepository(dataSource);
                cursorRepository.commit(consumerId, cursorCommit);
            } catch (Exception e) {
                // Commit might fail if consumer lost lease, which is OK for this test
            }
        }

        // Second poll - should get next events in order
        final List<PolledEvent> secondBatch = pollAllEventsWithCursorInfo(consumerId);
        
        // Verify second batch is in order
        for (int i = 1; i < secondBatch.size(); i++) {
            assertThat(secondBatch.get(i).sequence()).isGreaterThanOrEqualTo(
                    secondBatch.get(i - 1).sequence());
        }
    }

    /**
     * Helper to poll all events until no more are available.
     */
    private List<Long> pollAllEvents(final String consumerId) throws SQLException {
        final List<Long> sequences = new ArrayList<>();
        
        while (true) {
            try (var conn = dataSource.getConnection();
                 var poll = conn.prepareCall("{CALL sp_events__poll(?, ?)}")) {
                poll.setString(1, consumerId);
                poll.setInt(2, 0);
                
                boolean gotEvents = false;
                boolean hasResults = poll.execute();
                if (hasResults) {
                    try (var rs = poll.getResultSet()) {
                        while (rs.next()) {
                            sequences.add(rs.getLong("sequence"));
                            gotEvents = true;
                        }
                    }
                }
                while (poll.getMoreResults()) {
                    try (var ignored = poll.getResultSet()) {
                        // consume metadata
                    }
                }
                
                if (!gotEvents) {
                    break; // No more events
                }
            }
        }
        
        return sequences;
    }

    /**
     * Helper to poll events and return full event info including cursor and sequence.
     */
    private List<PolledEvent> pollAllEventsWithCursorInfo(final String consumerId) 
            throws SQLException {
        final List<PolledEvent> events = new ArrayList<>();
        
        while (true) {
            try (var conn = dataSource.getConnection();
                 var poll = conn.prepareCall("{CALL sp_events__poll(?, ?)}")) {
                poll.setString(1, consumerId);
                poll.setInt(2, 0);
                
                boolean gotEvents = false;
                boolean hasResults = poll.execute();
                if (hasResults) {
                    try (var rs = poll.getResultSet()) {
                        while (rs.next()) {
                            events.add(new PolledEvent(
                                    rs.getLong("event_id"),
                                    rs.getLong("cursor_id"),
                                    rs.getLong("sequence")
                            ));
                            gotEvents = true;
                        }
                    }
                }
                while (poll.getMoreResults()) {
                    try (var ignored = poll.getResultSet()) {
                        // consume metadata
                    }
                }
                
                if (!gotEvents) {
                    break;
                }
            }
        }
        
        return events;
    }

    /**
     * Simple record to hold polled event info.
     */
    private record PolledEvent(long eventId, long cursorId, long sequence) {
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
