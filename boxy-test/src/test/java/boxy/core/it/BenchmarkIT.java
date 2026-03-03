package boxy.core.it;

import boxy.core.domain.PublishRequest;
import boxy.core.repository.ConsumerRepository;
import boxy.core.repository.CursorRepository;
import boxy.core.repository.EventRepository;
import boxy.core.repository.PartitionRepository;
import boxy.core.repository.SubscriptionRepository;
import boxy.core.repository.TopicRepository;
import boxy.core.repository.NamespaceRepository;
import org.HdrHistogram.Histogram;
import org.HdrHistogram.Recorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.fail;

/**
 * Isolated component benchmarks for each hot path in the Boxy event pipeline.
 *
 * <p>Each test covers a single subsystem so that bottlenecks can be identified independently:
 * <ol>
 *   <li><b>Publish throughput</b> – events/sec into {@code events} + {@code unprocessed_events}</li>
 *   <li><b>Sequencer throughput</b> – events/sec processed by {@code sp_sequence} (MySQL event scheduler)</li>
 *   <li><b>Poll throughput</b> – events/sec returned to a consumer per {@code sp_events__poll} call</li>
 *   <li><b>Commit throughput</b> – cursor updates/sec via {@code sp_cursors__commit}</li>
 * </ol>
 *
 * <p>All tests use Testcontainers (bench-local profile) and are <em>not</em> representative of
 * production hardware. Baseline numbers belong in {@code docs/benchmarks.md}.
 */
@Testcontainers
public class BenchmarkIT extends BaseIT {

    private static final String PATH = "benchmark-ns";
    private static final String TOPIC = "this-is-topic-a";
    private static final String SUB   = "bench-component-sub";
    private static final int PARTITIONS = 16;
    private static final String DATA = "{\"key\": \"value\"}\n";

    private Recorder              recorder;
    private EventRepository       eventRepository;
    private TopicRepository       topicRepository;
    private PartitionRepository   partitionRepository;
    private NamespaceRepository   namespaceRepository;
    private SubscriptionRepository subscriptionRepository;
    private ConsumerRepository    consumerRepository;
    private CursorRepository      cursorRepository;

    @BeforeEach
    void setup() {
        eventRepository       = new EventRepository(dataSource);
        topicRepository       = new TopicRepository(dataSource);
        partitionRepository   = new PartitionRepository(dataSource);
        namespaceRepository   = new NamespaceRepository(dataSource);
        subscriptionRepository = new SubscriptionRepository(dataSource);
        consumerRepository    = new ConsumerRepository(dataSource);
        cursorRepository      = new CursorRepository(dataSource);
        namespaceRepository.create(PATH);
        recorder = new Recorder(TimeUnit.SECONDS.toNanos(1), 3);
    }

    @Test
    void publishAdvanced_singleThreaded() {
        topicRepository.create(PATH, TOPIC, PARTITIONS);
        final var partitionId = partitionRepository.find(PATH, TOPIC, 0).orElseThrow().id();

        final var histogram = new Histogram(TimeUnit.SECONDS.toNanos(1), 3);

        for (int run = 0; run < 3; run++) {
            histogram.reset();
            for (int i = 0; i < 2_000; i++) {
                final var start = System.nanoTime();
                eventRepository.publish(partitionId, DATA);
                histogram.recordValue(System.nanoTime() - start);
            }
            System.out.printf("Run #%d:%n", run);
            histogram.outputPercentileDistribution(System.out, 1, 1e6);
        }
    }

    @Test
    void publish_singleThreaded() {
        topicRepository.create(PATH, TOPIC, PARTITIONS);
        final var histogram = new Histogram(TimeUnit.SECONDS.toNanos(1), 3);

        for (int run = 0; run < 3; run++) {
            histogram.reset();
            for (int i = 0; i < 5_000; i++) {
                final var start = System.nanoTime();
                eventRepository.publish(PATH, TOPIC, "k" + i, DATA);
                histogram.recordValue(System.nanoTime() - start);
            }
            System.out.printf("Run #%d:%n", run);
            histogram.outputPercentileDistribution(System.out, 1, 1e6);
        }
    }

    @Test
    void publish_multiThreaded() throws InterruptedException, BrokenBarrierException {
        topicRepository.create(PATH, TOPIC, PARTITIONS);
        final var threadCount = 10;
        final var opsPerThread = 1_000;

        try (final var es = Executors.newFixedThreadPool(threadCount)) {
            final var startBarrier = new CyclicBarrier(threadCount + 1);
            final var doneLatch = new CountDownLatch(threadCount);

            // submit tasks
            IntStream.range(0, threadCount).forEach(thread ->
                    es.submit(() -> {
                        try {
                            startBarrier.await();
                            for (int j = 0; j < opsPerThread; j++) {
                                final var start = System.nanoTime();
                                eventRepository.publish(PATH, TOPIC, "k" + j, DATA);
                                recorder.recordValue(System.nanoTime() - start);
                            }
                        } catch (ArrayIndexOutOfBoundsException | BrokenBarrierException | InterruptedException e) {
                            fail("Task failed: " + e.getMessage());
                        } finally {
                            doneLatch.countDown();
                        }
                    })
            );

            // start all threads at once
            startBarrier.await();
            doneLatch.await();
            es.shutdown();
        }

        // retrieve the accumulated histogram
        final var snapshot = recorder.getIntervalHistogram();
        System.out.println("=== Multi-threaded stats ===");
        snapshot.outputPercentileDistribution(System.out, 1, 1e6);
        // get the event inserts per second
        final double totalTime = (snapshot.getEndTimeStamp() - snapshot.getStartTimeStamp());
        final double count = threadCount * opsPerThread;
        final var opsPerSecond = count / totalTime * 1_000;
        System.out.println("Throughput = " + opsPerSecond + " events inserted per second");
    }

    // =========================================================================
    // (a+) Batch publish throughput  (item #32 benchmark gate)
    // =========================================================================

    /**
     * Compares single-event publish vs batch publish throughput, providing the
     * benchmark gate for item #32 (sp_events__publish_multi v2).
     *
     * <p>For N events, batch publish should show ≥ N × single-event throughput due
     * to eliminating N-1 extra transaction commits and JDBC round-trips.
     */
    @Test
    void publishBatch_vs_singlePublish() {
        topicRepository.create(PATH, TOPIC, PARTITIONS);

        final int batchSize  = 50;
        final int iterations = 100;
        final int totalEvents = batchSize * iterations;

        // --- Single-event baseline ---
        final var singleHistogram = new Histogram(TimeUnit.SECONDS.toNanos(10), 3);
        final long singleStart = System.nanoTime();
        for (int i = 0; i < totalEvents; i++) {
            final long t = System.nanoTime();
            eventRepository.publish(PATH, TOPIC, "k" + i, DATA);
            singleHistogram.recordValue(System.nanoTime() - t);
        }
        final long singleEnd = System.nanoTime();

        // --- Batch publish ---
        final var batchHistogram = new Histogram(TimeUnit.SECONDS.toNanos(10), 3);
        final long batchStart = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            final int base = i * batchSize;
            final var batch = new java.util.ArrayList<PublishRequest>(batchSize);
            for (int j = 0; j < batchSize; j++) {
                batch.add(new PublishRequest(PATH, TOPIC, "k" + (base + j), DATA));
            }
            final long t = System.nanoTime();
            eventRepository.publishBatch(batch);
            batchHistogram.recordValue(System.nanoTime() - t);
        }
        final long batchEnd = System.nanoTime();

        // --- Report ---
        final double singleSec = (singleEnd - singleStart) / 1e9;
        final double batchSec  = (batchEnd  - batchStart)  / 1e9;
        final double singleTps = totalEvents / singleSec;
        final double batchTps  = totalEvents / batchSec;

        System.out.printf("%n=== Publish throughput comparison (batch_size=%d) ===%n", batchSize);
        System.out.printf("  Single-event: %d events in %.3f s → %.0f events/sec%n",
                totalEvents, singleSec, singleTps);
        System.out.printf("  Batch publish: %d events in %.3f s → %.0f events/sec%n",
                totalEvents, batchSec, batchTps);
        System.out.printf("  Speedup: %.1fx%n", batchTps / singleTps);
        System.out.printf("  Single p99=%.3f ms  Batch p99=%.3f ms%n",
                singleHistogram.getValueAtPercentile(99) / 1e6,
                batchHistogram.getValueAtPercentile(99)  / 1e6);
    }

    // =========================================================================
    // (b) Sequencer throughput
    // =========================================================================

    /**
     * Measures how quickly the MySQL event scheduler drains {@code unprocessed_events}
     * into {@code sequences}. Publishes a fixed batch, then polls the {@code sequences}
     * table until all events appear, reporting events/sec and the drain wall time.
     *
     * <p>If the sequencer cannot reach 1M events/sec, {@code unprocessed_events} will
     * grow unboundedly under full load. This test establishes the current ceiling.
     */
    @Test
    void sequencer_throughput() throws SQLException, InterruptedException {
        topicRepository.create(PATH, TOPIC, PARTITIONS);
        final int eventCount = 1_000;

        // Publish batch of events
        for (int i = 0; i < eventCount; i++) {
            eventRepository.publish(PATH, TOPIC, "k" + i, DATA);
        }

        // Measure time until sequencer has processed all events
        final long sequenceStart = System.nanoTime();
        awaitSequenceCount(eventCount, 30_000);
        final long sequenceEnd = System.nanoTime();

        final double elapsedSec = (sequenceEnd - sequenceStart) / 1e9;
        final double throughput = eventCount / elapsedSec;

        System.out.printf("%n=== Sequencer throughput ===%n");
        System.out.printf("  Events    : %d%n", eventCount);
        System.out.printf("  Drain time: %.3f s%n", elapsedSec);
        System.out.printf("  Throughput: %.0f events/sec%n", throughput);
    }

    // =========================================================================
    // (c) Poll throughput
    // =========================================================================

    /**
     * Measures {@code sp_events__poll} call throughput with a pre-sequenced event batch.
     * Pre-stages events so the consumer has data to return on every call, isolating the
     * poll stored-procedure cost from publish and sequencer overhead.
     */
    @Test
    void poll_throughput() throws SQLException, InterruptedException {
        topicRepository.create(PATH, TOPIC, PARTITIONS);
        subscriptionRepository.create(SUB);
        subscriptionRepository.subscribe(SUB, PATH, TOPIC);

        final String consumerId = "bench-poll-consumer";
        consumerRepository.register(consumerId, SUB, List.of(PATH + "/" + TOPIC));

        // Pre-stage events: publish and wait for sequencer
        final int eventCount = 500;
        for (int i = 0; i < eventCount; i++) {
            eventRepository.publish(PATH, TOPIC, "k" + i, DATA);
        }
        awaitSequenceCount(eventCount, 30_000);

        // Benchmark poll calls
        final var histogram = new Histogram(TimeUnit.SECONDS.toNanos(10), 3);
        long totalPolled = 0;
        final long benchStart = System.nanoTime();

        try (var conn = dataSource.getConnection();
             var poll = conn.prepareCall("{CALL sp_events__poll(?, ?)}")) {
            poll.setString(1, consumerId);
            poll.setInt(2, 0);  // 0 = use SP default batch size (100)
            for (int i = 0; i < 2_000; i++) {
                final long start = System.nanoTime();
                final boolean hasResults = poll.execute();
                histogram.recordValue(System.nanoTime() - start);
                if (hasResults) {
                    try (var rs = poll.getResultSet()) {
                        while (rs.next()) totalPolled++;
                    }
                }
                while (poll.getMoreResults()) {
                    try (var ignored = poll.getResultSet()) { /* consume metadata */ }
                }
            }
        }

        final long benchEnd = System.nanoTime();
        final double elapsedSec = (benchEnd - benchStart) / 1e9;

        System.out.printf("%n=== Poll throughput ===%n");
        System.out.printf("  Poll calls  : 2000%n");
        System.out.printf("  Total polled: %d events%n", totalPolled);
        System.out.printf("  Elapsed     : %.3f s%n", elapsedSec);
        System.out.printf("  Calls/sec   : %.0f%n", 2_000 / elapsedSec);
        System.out.printf("  p50=%.3f ms  p99=%.3f ms  p999=%.3f ms%n",
                histogram.getValueAtPercentile(50)   / 1e6,
                histogram.getValueAtPercentile(99)   / 1e6,
                histogram.getValueAtPercentile(99.9) / 1e6);
    }

    // =========================================================================
    // (d) Commit throughput
    // =========================================================================

    /**
     * Measures {@code sp_cursors__commit} throughput in isolation.
     * Pre-polls a batch of events to obtain valid (cursor_id, sequence) pairs, then
     * benchmarks repeated commit calls, isolating commit stored-procedure cost.
     */
    @Test
    void commit_throughput() throws SQLException, InterruptedException {
        topicRepository.create(PATH, TOPIC, PARTITIONS);
        subscriptionRepository.create(SUB);
        subscriptionRepository.subscribe(SUB, PATH, TOPIC);

        final String consumerId = "bench-commit-consumer";
        consumerRepository.register(consumerId, SUB, List.of(PATH + "/" + TOPIC));

        // Pre-stage a single event to get a valid cursor_id + sequence pair for committing
        eventRepository.publish(PATH, TOPIC, "k0", DATA);
        awaitSequenceCount(1, 30_000);

        // Get the first cursor_id and sequence via a single poll
        final Map<Long, Long> cursorMap = new HashMap<>();
        try (var conn = dataSource.getConnection();
             var poll = conn.prepareCall("{CALL sp_events__poll(?, ?)}")) {
            poll.setString(1, consumerId);
            poll.setInt(2, 0);  // 0 = use SP default batch size (100)
            if (poll.execute()) {
                try (var rs = poll.getResultSet()) {
                    if (rs.next()) {
                        cursorMap.put(rs.getLong("cursor_id"), rs.getLong("sequence"));
                    }
                }
            }
            while (poll.getMoreResults()) {
                try (var ignored = poll.getResultSet()) { /* consume metadata */ }
            }
        }

        if (cursorMap.isEmpty()) {
            System.err.println("WARNING: No events polled; skipping commit benchmark");
            return;
        }

        // Benchmark commit calls (re-committing the same cursor/sequence is idempotent)
        final var histogram = new Histogram(TimeUnit.SECONDS.toNanos(10), 3);
        final int commitCount = 2_000;
        final long benchStart = System.nanoTime();

        for (int i = 0; i < commitCount; i++) {
            final long start = System.nanoTime();
            cursorRepository.commit(consumerId, cursorMap);
            histogram.recordValue(System.nanoTime() - start);
        }

        final long benchEnd = System.nanoTime();
        final double elapsedSec = (benchEnd - benchStart) / 1e9;

        System.out.printf("%n=== Commit throughput ===%n");
        System.out.printf("  Commit calls: %d%n", commitCount);
        System.out.printf("  Elapsed     : %.3f s%n", elapsedSec);
        System.out.printf("  Commits/sec : %.0f%n", commitCount / elapsedSec);
        System.out.printf("  p50=%.3f ms  p99=%.3f ms  p999=%.3f ms%n",
                histogram.getValueAtPercentile(50)   / 1e6,
                histogram.getValueAtPercentile(99)   / 1e6,
                histogram.getValueAtPercentile(99.9) / 1e6);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Blocks until the {@code sequences} table contains at least {@code expectedCount} rows,
     * polling every 100 ms. Logs a warning if the timeout expires first.
     *
     * @param expectedCount minimum row count required before returning
     * @param timeoutMs     maximum time to wait in milliseconds
     */
    private void awaitSequenceCount(final long expectedCount, final long timeoutMs)
            throws InterruptedException, SQLException {
        final long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try (var conn = dataSource.getConnection();
                 var ps   = conn.prepareStatement("SELECT COUNT(*) FROM sequences");
                 var rs   = ps.executeQuery()) {
                if (rs.next() && rs.getLong(1) >= expectedCount) {
                    return;
                }
            }
            Thread.sleep(100);
        }
        System.err.printf("WARNING: sequences table did not reach %d rows within %d ms%n",
                expectedCount, timeoutMs);
    }

}
