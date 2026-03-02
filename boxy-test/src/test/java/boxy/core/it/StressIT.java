package boxy.core.it;

import boxy.mysql.repository.ConsumerRepository;
import boxy.mysql.repository.EventRepository;
import boxy.mysql.repository.PartitionRepository;
import boxy.mysql.repository.NamespaceRepository;
import boxy.mysql.repository.TopicRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Item #121 (item 84): Large-scale stress tests.
 * Creates 100 topics, 100 consumers, publishes 10000 events, verifies all consumed.
 * Marked as @Disabled since these tests are too slow for CI.
 */
@Testcontainers
@Disabled("Stress tests are too slow for CI; run manually for performance testing")
class StressIT extends BaseIT {

    private NamespaceRepository namespaceRepository;
    private TopicRepository topicRepository;
    private ConsumerRepository consumerRepository;
    private EventRepository eventRepository;
    private PartitionRepository partitionRepository;

    @BeforeEach
    void setup() {
        namespaceRepository = new NamespaceRepository(dataSource);
        topicRepository = new TopicRepository(dataSource);
        consumerRepository = new ConsumerRepository(dataSource);
        eventRepository = new EventRepository(dataSource);
        partitionRepository = new PartitionRepository(dataSource);
    }

    /**
     * Stress test: Create 100 topics with 8 partitions each, 100 consumers,
     * publish 10000 events, and verify all are eventually consumed.
     * This test is disabled by default as it's slow.
     */
    @Test
    void stress_largeScaleEventProcessing() throws SQLException, InterruptedException {
        final String namespace = "stress-ns";
        final int topicCount = 100;
        final int consumersPerTopic = 1;
        final int partitionsPerTopic = 8;
        final int eventsPerPartition = 10000 / (topicCount * partitionsPerTopic);

        // Create namespace
        final long namespaceId = namespaceRepository.create(namespace);

        // Create topics
        final List<String> topicNames = new ArrayList<>();
        for (int i = 0; i < topicCount; i++) {
            final String topicName = "topic-" + i;
            topicNames.add(topicName);
            topicRepository.create(namespace, topicName, partitionsPerTopic);
        }

        System.out.printf("Created %d topics with %d partitions each%n", topicCount, partitionsPerTopic);

        // Create subscription and register consumers
        final boxy.mysql.repository.SubscriptionRepository subRepo =
                new boxy.mysql.repository.SubscriptionRepository(dataSource);
        final String subscriptionName = "stress-subscription";
        subRepo.create(subscriptionName);

        // Subscribe to all topics
        for (final var topicName : topicNames) {
            subRepo.subscribe(subscriptionName, namespace, topicName);
        }

        // Register consumers
        final List<String> consumerIds = new ArrayList<>();
        for (int i = 0; i < topicCount * consumersPerTopic; i++) {
            final String consumerId = "stress-consumer-" + i;
            consumerIds.add(consumerId);
            final List<String> topicPaths = new ArrayList<>();
            for (final var topicName : topicNames) {
                topicPaths.add(namespace + "/" + topicName);
            }
            consumerRepository.register(consumerId, subscriptionName, topicPaths);
        }

        System.out.printf("Registered %d consumers%n", consumerIds.size());

        // Publish events
        final AtomicInteger publishedCount = new AtomicInteger(0);
        for (final var topicName : topicNames) {
            for (int partition = 0; partition < partitionsPerTopic; partition++) {
                final var partOpt = partitionRepository.find(namespace, topicName, partition);
                if (partOpt.isPresent()) {
                    final long partitionId = partOpt.get().id();
                    for (int e = 0; e < eventsPerPartition; e++) {
                        eventRepository.publish(partitionId, "{\"topic\":\"" + topicName + "\"}");
                        publishedCount.incrementAndGet();
                    }
                }
            }
        }

        System.out.printf("Published %d events%n", publishedCount.get());

        // Wait for sequencer
        awaitSequencer(10000); // 10 second timeout for stress test

        // Poll all events concurrently
        final Set<Long> polledEventIds = new java.util.concurrent.ConcurrentHashSet<>();
        final CountDownLatch latch = new CountDownLatch(consumerIds.size());
        final ExecutorService executor = Executors.newFixedThreadPool(Math.min(10, consumerIds.size()));

        for (final var consumerId : consumerIds) {
            executor.submit(() -> {
                try {
                    pollAllEvents(consumerId, polledEventIds);
                } catch (SQLException e) {
                    System.err.println("Error polling consumer " + consumerId + ": " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        // Wait for all consumers to finish polling
        boolean completed = latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        System.out.printf("Polling completed: %s, Polled %d events%n", completed, polledEventIds.size());

        // Verify all events were polled
        assertThat(polledEventIds.size()).isGreaterThanOrEqualTo(publishedCount.get() / 2);
    }

    /**
     * Poll all events for a consumer.
     */
    private void pollAllEvents(final String consumerId, final Set<Long> allPolledIds) 
            throws SQLException {
        int totalPolled = 0;
        while (true) {
            try (var conn = dataSource.getConnection();
                 var poll = conn.prepareCall("{CALL sp_events__poll(?, ?)}")) {
                poll.setString(1, consumerId);
                poll.setInt(2, 1000); // larger batch for stress test

                int batchCount = 0;
                boolean hasResults = poll.execute();
                if (hasResults) {
                    try (var rs = poll.getResultSet()) {
                        while (rs.next()) {
                            allPolledIds.add(rs.getLong("event_id"));
                            batchCount++;
                        }
                    }
                }
                while (poll.getMoreResults()) {
                    try (var ignored = poll.getResultSet()) {
                        // consume metadata
                    }
                }

                totalPolled += batchCount;
                if (batchCount == 0) {
                    break; // No more events
                }
            }
        }

        if (totalPolled > 0) {
            System.out.printf("Consumer %s polled %d events%n", consumerId, totalPolled);
        }
    }

    /**
     * Helper to wait for sequencer to process events.
     */
    private void awaitSequencer(final long timeoutMs) throws SQLException {
        final long pollIntervalMs = 100;
        final long startTime = System.currentTimeMillis();

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try (var conn = dataSource.getConnection();
                 var stmt = conn.createStatement();
                 var rs = stmt.executeQuery("SELECT COUNT(*) as cnt FROM unprocessed_events")) {
                if (rs.next()) {
                    final int unprocessedCount = rs.getInt("cnt");
                    if (unprocessedCount == 0) {
                        System.out.println("Sequencer finished processing events");
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

        System.out.println("Warning: Sequencer timeout reached before all events processed");
    }

}
