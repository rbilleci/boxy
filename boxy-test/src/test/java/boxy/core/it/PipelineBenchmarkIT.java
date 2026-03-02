package boxy.core.it;

import boxy.mysql.repository.ConsumerRepository;
import boxy.mysql.repository.CursorRepository;
import boxy.mysql.repository.EventRepository;
import boxy.mysql.repository.NamespaceRepository;
import boxy.mysql.repository.SubscriptionRepository;
import boxy.mysql.repository.TopicRepository;
import org.HdrHistogram.Histogram;
import org.HdrHistogram.Recorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

/**
 * End-to-end pipeline benchmark covering the complete Boxy event lifecycle:
 * publish → sequence (MySQL event scheduler) → poll → commit.
 *
 * <p>Metrics reported for each scenario:
 * <ul>
 *   <li><b>Publish throughput</b> – events inserted per second</li>
 *   <li><b>Sequence time</b> – wall-clock time for the sequencer to drain unprocessed_events</li>
 *   <li><b>Consume throughput</b> – events polled+committed per second</li>
 *   <li><b>End-to-end wall time</b> – first publish to last commit</li>
 *   <li><b>Sequencer lag</b> – depth of unprocessed_events immediately after publish</li>
 *   <li><b>Publish p50/p99/p999 latency</b> – per-call HdrHistogram percentiles (ms)</li>
 *   <li><b>Poll p50/p99/p999 latency</b> – per-call HdrHistogram percentiles (ms)</li>
 * </ul>
 *
 * <p>These tests use Testcontainers with tmpfs and relaxed durability (bench-local profile).
 * Numbers are <em>not</em> representative of production hardware; they serve as a regression
 * baseline. See {@code docs/benchmarks.md} for methodology and hardware-specific numbers.
 */
@Testcontainers
public class PipelineBenchmarkIT extends BaseIT {

    private static final String NS    = "bench-pipeline";
    private static final String TOPIC = "bench-topic";
    private static final String SUB   = "bench-sub";
    private static final int    PARTITIONS = 16;
    private static final String DATA  = "{\"v\":1}";

    private NamespaceRepository     namespaceRepository;
    private TopicRepository         topicRepository;
    private SubscriptionRepository  subscriptionRepository;
    private ConsumerRepository      consumerRepository;
    private CursorRepository        cursorRepository;
    private EventRepository         eventRepository;

    @BeforeEach
    void setup() {
        namespaceRepository    = new NamespaceRepository(dataSource);
        topicRepository        = new TopicRepository(dataSource);
        subscriptionRepository = new SubscriptionRepository(dataSource);
        consumerRepository     = new ConsumerRepository(dataSource);
        cursorRepository       = new CursorRepository(dataSource);
        eventRepository        = new EventRepository(dataSource);

        namespaceRepository.create(NS);
        topicRepository.create(NS, TOPIC, PARTITIONS);
        subscriptionRepository.create(SUB);
        subscriptionRepository.subscribe(SUB, NS, TOPIC);
    }

    // -------------------------------------------------------------------------
    // Test scenarios
    // -------------------------------------------------------------------------

    /**
     * Single producer, single consumer — minimal contention baseline.
     */
    @Test
    void pipeline_singleProducerSingleConsumer() throws Exception {
        runPipeline(1, 1, 2_000, "1P-1C");
    }

    /**
     * Four producers, four consumers — moderate parallelism.
     */
    @Test
    void pipeline_fourProducersFourConsumers() throws Exception {
        runPipeline(4, 4, 500, "4P-4C");
    }

    /**
     * Eight producers, two consumers — publish-heavy workload.
     */
    @Test
    void pipeline_eightProducersTwoConsumers() throws Exception {
        runPipeline(8, 2, 250, "8P-2C");
    }

    // -------------------------------------------------------------------------
    // Core benchmark logic
    // -------------------------------------------------------------------------

    /**
     * Runs a full pipeline benchmark: publish → await sequencer → poll+commit,
     * then prints a structured report to stdout.
     *
     * @param producerCount    number of concurrent producer threads
     * @param consumerCount    number of concurrent consumer threads
     * @param eventsPerProducer events each producer publishes
     * @param label            label for the printed report
     */
    private void runPipeline(final int producerCount,
                              final int consumerCount,
                              final int eventsPerProducer,
                              final String label)
            throws InterruptedException, BrokenBarrierException, SQLException {

        final int totalEvents = producerCount * eventsPerProducer;

        // HdrHistogram: max 10 s, 3 significant digits
        final var publishRecorder = new Recorder(TimeUnit.SECONDS.toNanos(10), 3);
        final var pollRecorder    = new Recorder(TimeUnit.SECONDS.toNanos(10), 3);
        final var totalConsumed   = new AtomicLong(0);

        // Register consumers before publishing so cursors exist when events arrive
        final List<String> consumerIds = new ArrayList<>(consumerCount);
        for (int i = 0; i < consumerCount; i++) {
            final String cid = "bench-c-" + i;
            consumerIds.add(cid);
            consumerRepository.register(cid, SUB, List.of(NS + "/" + TOPIC));
        }

        // -----------------------------------------------------------------
        // Phase 1: Publish
        // -----------------------------------------------------------------
        final long publishStart = System.nanoTime();

        try (final var producerPool = Executors.newFixedThreadPool(producerCount)) {
            final var barrier     = new CyclicBarrier(producerCount + 1);
            final var doneLatch   = new CountDownLatch(producerCount);

            IntStream.range(0, producerCount).forEach(p ->
                    producerPool.submit(() -> {
                        try {
                            barrier.await();
                            for (int i = 0; i < eventsPerProducer; i++) {
                                final long start = System.nanoTime();
                                eventRepository.publish(NS, TOPIC, "k" + i, DATA);
                                publishRecorder.recordValue(System.nanoTime() - start);
                            }
                        } catch (Exception e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            doneLatch.countDown();
                        }
                    })
            );

            barrier.await();
            doneLatch.await();
        }

        final long publishEnd = System.nanoTime();

        // -----------------------------------------------------------------
        // Measure sequencer lag immediately after publish
        // -----------------------------------------------------------------
        final long lagAfterPublish = queryUnprocessedCount();

        // -----------------------------------------------------------------
        // Phase 2: Wait for sequencer to drain unprocessed_events
        // -----------------------------------------------------------------
        awaitSequencerDrained(totalEvents, 60_000);
        final long sequencerEnd = System.nanoTime();

        // -----------------------------------------------------------------
        // Phase 3: Poll + commit
        // -----------------------------------------------------------------
        final long consumeStart = System.nanoTime();

        try (final var consumerPool = Executors.newFixedThreadPool(consumerCount)) {
            final var barrier   = new CyclicBarrier(consumerCount + 1);
            final var doneLatch = new CountDownLatch(consumerCount);

            for (final String cid : consumerIds) {
                consumerPool.submit(() -> {
                    try {
                        barrier.await();
                        int emptyPolls = 0;
                        while (emptyPolls < 30) {
                            final long start = System.nanoTime();
                            final long batch = pollAndCommit(cid);
                            pollRecorder.recordValue(System.nanoTime() - start);
                            if (batch > 0) {
                                totalConsumed.addAndGet(batch);
                                emptyPolls = 0;
                            } else {
                                emptyPolls++;
                                Thread.sleep(50);
                            }
                        }
                    } catch (Exception e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            barrier.await();
            doneLatch.await();
        }

        final long consumeEnd = System.nanoTime();

        // -----------------------------------------------------------------
        // Report
        // -----------------------------------------------------------------
        final var publishSnap = publishRecorder.getIntervalHistogram();
        final var pollSnap    = pollRecorder.getIntervalHistogram();

        final double publishSec  = (publishEnd  - publishStart) / 1e9;
        final double sequenceSec = (sequencerEnd - publishEnd)  / 1e9;
        final double consumeSec  = (consumeEnd  - consumeStart) / 1e9;
        final double totalSec    = (consumeEnd  - publishStart) / 1e9;

        System.out.printf("%n=== Pipeline Benchmark [%s] ===%n", label);
        System.out.printf("  Producers/consumers : %d / %d%n", producerCount, consumerCount);
        System.out.printf("  Total events        : %d%n", totalEvents);
        System.out.printf("  Total consumed      : %d%n", totalConsumed.get());
        System.out.printf("  Sequencer lag       : %d unprocessed after publish%n", lagAfterPublish);
        System.out.printf("  Publish time        : %.3f s  →  %.0f events/sec%n",
                publishSec, totalEvents / publishSec);
        System.out.printf("  Sequence time       : %.3f s%n", sequenceSec);
        System.out.printf("  Consume time        : %.3f s  →  %.0f events/sec%n",
                consumeSec, totalConsumed.get() / Math.max(consumeSec, 0.001));
        System.out.printf("  End-to-end wall     : %.3f s%n", totalSec);

        printHistogram("Publish latency (ms)", publishSnap);
        printHistogram("Poll latency    (ms)", pollSnap);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Executes one {@code sp_events__poll} call followed by a commit of all returned events.
     *
     * @param consumerId the consumer identifier
     * @return number of events received in this poll batch
     */
    private long pollAndCommit(final String consumerId) throws SQLException {
        final Map<Long, Long> commits = new HashMap<>();
        long count = 0;

        try (var conn  = dataSource.getConnection();
             var poll  = conn.prepareCall("{CALL sp_events__poll(?)}")) {

            poll.setString(1, consumerId);
            boolean hasResults = poll.execute();

            if (hasResults) {
                try (var rs = poll.getResultSet()) {
                    while (rs.next()) {
                        commits.put(rs.getLong("cursor_id"), rs.getLong("sequence"));
                        count++;
                    }
                }
            }
            while (poll.getMoreResults()) {
                try (var ignored = poll.getResultSet()) { /* consume metadata result set */ }
            }
        }

        if (!commits.isEmpty()) {
            cursorRepository.commit(consumerId, commits);
        }
        return count;
    }

    /**
     * Returns the current depth of the {@code unprocessed_events} table.
     */
    private long queryUnprocessedCount() {
        try (var conn = dataSource.getConnection();
             var ps   = conn.prepareStatement("SELECT COUNT(*) FROM unprocessed_events");
             var rs   = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        } catch (SQLException e) {
            return -1L;
        }
    }

    /**
     * Blocks until {@code sequences} contains at least {@code expectedCount} rows
     * (i.e. the MySQL event scheduler has processed all published events).
     *
     * @param expectedCount minimum sequences row count to wait for
     * @param timeoutMs     maximum wait time in milliseconds
     */
    private void awaitSequencerDrained(final long expectedCount, final long timeoutMs)
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
        System.err.printf("WARNING: Sequencer did not drain %d events within %d ms%n",
                expectedCount, timeoutMs);
    }

    /**
     * Prints p50 / p99 / p999 percentiles from an {@link Histogram} snapshot.
     *
     * @param title     label for the output block
     * @param histogram already-captured interval histogram
     */
    private static void printHistogram(final String title, final Histogram histogram) {
        System.out.printf("  --- %s ---%n", title);
        System.out.printf("      p50=%.3f  p99=%.3f  p999=%.3f%n",
                histogram.getValueAtPercentile(50)   / 1e6,
                histogram.getValueAtPercentile(99)   / 1e6,
                histogram.getValueAtPercentile(99.9) / 1e6);
    }
}
