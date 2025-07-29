package boxy.core.it;

import boxy.core.repository.EventRepository;
import boxy.core.repository.PartitionRepository;
import boxy.core.repository.TopicRepository;
import org.HdrHistogram.Histogram;
import org.HdrHistogram.Recorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.fail;

@Testcontainers
public class BenchmarkIT extends BaseIT {

    private static final String TENANT = UUID.randomUUID().toString();
    private static final String TOPIC = "this-is-topic-a";
    private static final int PARTITIONS = 16;
    private static final String DATA = "{\"key\": \"value\"}\n";

    private Recorder recorder;
    private EventRepository eventRepository;
    private TopicRepository topicRepository;
    private PartitionRepository partitionRepository;

    @BeforeEach
    void setup() {
        eventRepository = new EventRepository(dataSource);
        topicRepository = new TopicRepository(dataSource);
        partitionRepository = new PartitionRepository(dataSource);
        recorder = new Recorder(TimeUnit.SECONDS.toNanos(1), 3);
    }

    @Test
    void publishAdvanced_singleThreaded() {
        topicRepository.create(TENANT, TOPIC, PARTITIONS);
        final var partitionId = partitionRepository.find(TENANT, TOPIC, 0).orElseThrow().id();

        final var histogram = new Histogram(TimeUnit.SECONDS.toNanos(1), 3);

        for (int run = 0; run < 4; run++) {
            histogram.reset();
            for (int i = 0; i < 10_000; i++) {
                final var start = System.nanoTime();
                eventRepository.publishAdvanced(partitionId, DATA);
                histogram.recordValue(System.nanoTime() - start);
            }
            System.out.printf("Run #%d:%n", run);
            histogram.outputPercentileDistribution(System.out, 1, 1e6);
        }
    }

    @Test
    void publish_singleThreaded() {
        topicRepository.create(TENANT, TOPIC, PARTITIONS);
        final var histogram = new Histogram(TimeUnit.SECONDS.toNanos(1), 3);

        for (int run = 0; run < 4; run++) {
            histogram.reset();
            for (int i = 0; i < 10_000; i++) {
                final var start = System.nanoTime();
                eventRepository.publish(TENANT, TOPIC, "k" + i, DATA);
                histogram.recordValue(System.nanoTime() - start);
            }
            System.out.printf("Run #%d:%n", run);
            histogram.outputPercentileDistribution(System.out, 1, 1e6);
        }
    }

    @Test
    void publish_multiThreaded() throws InterruptedException, BrokenBarrierException {
        topicRepository.create(TENANT, TOPIC, PARTITIONS);
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
                                eventRepository.publish(TENANT, TOPIC, "k" + j, DATA);
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

}
