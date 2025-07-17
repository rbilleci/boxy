package boxy.persistence.it;

import boxy.persistence.dao.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@Testcontainers
public class BenchmarkIT extends BaseIT {

    private static final String TENANT_1 = "t1";
    private static final String TOPIC_A = "topic-a";
    private static final int DEFAULT_PARTITIONS = 16;
    private static final String DATA = """
            {"key": "value"}
            """;

    private EventDao eventDao;
    private TopicDao topicDao;
    private PartitionDao partitionDao;

    @BeforeEach
    void setup() {
        eventDao = jdbi.onDemand(EventDao.class);
        topicDao = jdbi.onDemand(TopicDao.class);
        partitionDao = jdbi.onDemand(PartitionDao.class);
    }

    @Test
    void publish_benchmark_experiment() {
        // Create a topic to publish to
        topicDao.create(TENANT_1, TOPIC_A, DEFAULT_PARTITIONS);
        for (int j = 0; j < 4; j++) {
            final long start = System.currentTimeMillis();
            for (int i = 0; i < 1_000; i++) {
                eventDao.publish(TENANT_1, TOPIC_A, "k", DATA);
            }
            final long end = System.currentTimeMillis();
            final long runTime = end - start;
            System.out.println(runTime);
        }
    }

    @Test
    void publish_benchmarkMultiThread_experiment() throws InterruptedException, BrokenBarrierException {

        topicDao.create(TENANT_1, TOPIC_A, DEFAULT_PARTITIONS);
        final var threadCount = 10;
        final var opsPerThread = 10_000;

        try (final var exec = Executors.newFixedThreadPool(threadCount)) {
            final var startBarrier = new CyclicBarrier(threadCount + 1);
            final var doneLatch = new CountDownLatch(threadCount);

            // submit tasks
            IntStream.range(0, threadCount).forEach(i -> exec.submit(() -> {
                try {
                    // wait for all threads to be ready
                    startBarrier.await();

                    final var start = System.nanoTime();
                    for (int j = 0; j < opsPerThread; j++) {
                        eventDao.publish(TENANT_1, TOPIC_A, "k" + j, DATA);
                    }
                    final var durationNs = System.nanoTime() - start;
                    System.out.printf("Thread %2d: %d ms%n", i, TimeUnit.NANOSECONDS.toMillis(durationNs));
                } catch (Exception e) {
                    fail("Task failed: " + e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            }));

            // release threads
            final var totalStart = System.nanoTime();
            startBarrier.await();

            // wait for completion
            doneLatch.await();
            final var totalDurationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - totalStart);

            System.out.printf("ALL THREADS DONE: total %d ms for %,d publishes%n", totalDurationMs, threadCount * opsPerThread);

            exec.shutdown();
        }
    }

    @Test
    void publish_benchmarkBatch_experiment() {
        // Create a topic to publish to
        topicDao.create(TENANT_1, TOPIC_A, DEFAULT_PARTITIONS);
        for (int j = 0; j < 4; j++) {
            final long start = System.currentTimeMillis();
            for (int i = 0; i < 1_000; i++) {
                final var batch = new ArrayList<String>(10);
                for (int x = 0; x < 10; x++) {
                    batch.add(DATA);
                }
                eventDao.publish(TENANT_1, TOPIC_A, "k", batch);
            }
            final long end = System.currentTimeMillis();
            final long runTime = end - start;
            System.out.println(runTime);
        }
    }

}
