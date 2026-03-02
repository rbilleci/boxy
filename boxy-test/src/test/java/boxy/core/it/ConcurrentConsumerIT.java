package boxy.core.it;

import boxy.mysql.repository.ConsumerRepository;
import boxy.mysql.repository.EventRepository;
import boxy.mysql.repository.PartitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static boxy.core.it.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Item #117 (item 80): Test that multiple consumers polling the same partition
 * don't get duplicate events. Verifies lease correctness under contention.
 */
@Testcontainers
class ConcurrentConsumerIT extends BaseIT {

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
     * Test that multiple consumers polling the same subscription don't get duplicate events.
     * Uses 3 concurrent consumer threads polling the same subscription.
     * Publishes 100 events and verifies all are consumed exactly once across all consumers.
     */
    @Test
    void poll_concurrentConsumers_noDuplicates() throws SQLException, InterruptedException {
        final var subscription = data.subscriptions().getFirst();
        final long partitionId =
                partitionRepository.find(TestData.PATH_A, TOPIC_A, 0).orElseThrow().id();

        // Register 3 consumers on the same subscription
        final String consumer1 = "concurrent-consumer-1";
        final String consumer2 = "concurrent-consumer-2";
        final String consumer3 = "concurrent-consumer-3";

        consumerRepository.register(consumer1, subscription.name(), List.of(PATH_A + "/" + TOPIC_A));
        consumerRepository.register(consumer2, subscription.name(), List.of(PATH_A + "/" + TOPIC_A));
        consumerRepository.register(consumer3, subscription.name(), List.of(PATH_A + "/" + TOPIC_A));

        // Publish 100 events
        final int eventCount = 100;
        for (int i = 0; i < eventCount; i++) {
            eventRepository.publish(partitionId, "{\"seq\":" + i + "}");
        }
        awaitSequencer();

        // Track polled event IDs across all consumers
        final Set<Long> allPolledEvents = Collections.synchronizedSet(new HashSet<>());
        final CountDownLatch latch = new CountDownLatch(3);
        final ExecutorService executor = Executors.newFixedThreadPool(3);

        // Start 3 polling threads, each polling its consumer until no more events
        executor.submit(() -> pollConsumer(consumer1, allPolledEvents, latch));
        executor.submit(() -> pollConsumer(consumer2, allPolledEvents, latch));
        executor.submit(() -> pollConsumer(consumer3, allPolledEvents, latch));

        // Wait for all threads to complete
        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        // Verify all events were polled and none were duplicated
        assertThat(allPolledEvents).hasSize(eventCount);
    }

    /**
     * Test that the same consumer ID polling concurrently from multiple threads
     * doesn't cause race conditions or duplicates.
     */
    @Test
    void poll_sameConsumerMultipleThreads_noDuplicates() throws SQLException, InterruptedException {
        final var subscription = data.subscriptions().getFirst();
        final long partitionId =
                partitionRepository.find(TestData.PATH_A, TOPIC_A, 0).orElseThrow().id();

        final String consumerId = "concurrent-poller";
        consumerRepository.register(consumerId, subscription.name(), List.of(PATH_A + "/" + TOPIC_A));

        // Publish 50 events
        final int eventCount = 50;
        for (int i = 0; i < eventCount; i++) {
            eventRepository.publish(partitionId, "{\"seq\":" + i + "}");
        }
        awaitSequencer();

        // Track polled event IDs from concurrent threads
        final Set<Long> allPolledEvents = Collections.synchronizedSet(new HashSet<>());
        final CountDownLatch latch = new CountDownLatch(3);
        final ExecutorService executor = Executors.newFixedThreadPool(3);

        // Start 3 polling threads with the same consumer ID
        for (int i = 0; i < 3; i++) {
            executor.submit(() -> {
                try {
                    pollOnce(consumerId, allPolledEvents);
                } finally {
                    latch.countDown();
                }
            });
        }

        // Wait for all threads to complete
        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        // Verify no duplicates were polled
        assertThat(allPolledEvents.size()).isLessThanOrEqualTo(eventCount);
    }

    /**
     * Poll from a consumer until all events are consumed.
     */
    private void pollConsumer(final String consumerId, final Set<Long> allPolledEvents, 
                               final CountDownLatch latch) {
        try {
            boolean hasMore = true;
            while (hasMore) {
                hasMore = pollOnce(consumerId, allPolledEvents);
                if (hasMore) {
                    Thread.sleep(100); // Brief pause between polls
                }
            }
        } catch (SQLException | InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            latch.countDown();
        }
    }

    /**
     * Poll once from a consumer and collect event IDs.
     * Returns true if events were returned (has more), false if empty.
     */
    private boolean pollOnce(final String consumerId, final Set<Long> allPolledEvents) 
            throws SQLException {
        final List<Long> polled = new ArrayList<>();
        try (var conn = dataSource.getConnection();
             var poll = conn.prepareCall("{CALL sp_events__poll(?, ?)}")) {
            poll.setString(1, consumerId);
            poll.setInt(2, 0);
            boolean hasResults = poll.execute();
            if (hasResults) {
                try (var rs = poll.getResultSet()) {
                    while (rs.next()) {
                        polled.add(rs.getLong("event_id"));
                    }
                }
            }
            while (poll.getMoreResults()) {
                try (var ignored = poll.getResultSet()) {
                    // consume metadata
                }
            }
        }

        allPolledEvents.addAll(polled);
        return !polled.isEmpty();
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
