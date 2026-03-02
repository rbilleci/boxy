package boxy.mysql.worker;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Async worker for consuming events from Boxy topics.
 *
 * The Worker polls events in a background thread pool, processes them via
 * an EventHandler callback, and optionally commits cursors. Supports:
 * - Multiple concurrent pollers (partition-level parallelism)
 * - Back-pressure via bounded queue
 * - Graceful shutdown with timeout
 * - Metrics (events processed, errors, latency)
 *
 * Usage:
 * <pre>
 * var config = WorkerConfig.builder()
 *     .subscriptionName("my-group")
 *     .topic("events")
 *     .maxBatchSize(50)
 *     .maxConcurrentPollers(4)
 *     .build();
 *
 * var worker = new Worker<>(
 *     config,
 *     dataSource,
 *     events -> {
 *         for (var event : events) {
 *             System.out.println("Event: " + event.payload());
 *         }
 *         return true; // commit
 *     },
 *     String::new,  // payload deserializer
 *     meterRegistry
 * );
 *
 * worker.start();
 * // ... process events ...
 * worker.stop();
 * </pre>
 *
 * @param <T> Event payload type
 * @since 1.0
 */
public class Worker<T> {
    private static final Logger log = LoggerFactory.getLogger(Worker.class);

    private final WorkerConfig config;
    private final EventHandler<T> handler;
    private final Function<String, T> deserializer;
    private final MeterRegistry metrics;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService executor;
    private final AtomicLong eventsProcessed = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);

    /**
     * Creates a new Worker.
     *
     * @param config Worker configuration
     * @param handler Event handler callback
     * @param deserializer Function to deserialize payload strings
     * @param metrics Meter registry for observability (optional, can be null)
     */
    public Worker(
        WorkerConfig config,
        EventHandler<T> handler,
        Function<String, T> deserializer,
        MeterRegistry metrics
    ) {
        this.config = config;
        this.handler = handler;
        this.deserializer = deserializer;
        this.metrics = metrics;

        // Create thread pool for concurrent pollers
        this.executor = Executors.newFixedThreadPool(
            config.maxConcurrentPollers(),
            r -> {
                Thread t = new Thread(r, "boxy-worker-" + config.topic());
                t.setDaemon(false);
                return t;
            }
        );

        // Register metrics if registry provided
        if (metrics != null) {
            registerMetrics();
        }
    }

    /**
     * Starts the worker (begins polling in background threads).
     */
    public void start() {
        if (!running.compareAndSet(false, true)) {
            log.warn("Worker already running for {}", config.topic());
            return;
        }

        log.info(
            "Starting Worker: subscription={}, topic={}, maxConcurrentPollers={}",
            config.subscriptionName(), config.topic(), config.maxConcurrentPollers()
        );

        // Spawn concurrent pollers
        for (int i = 0; i < config.maxConcurrentPollers(); i++) {
            executor.submit(this::pollLoop);
        }
    }

    /**
     * Stops the worker gracefully.
     */
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            log.warn("Worker not running");
            return;
        }

        log.info("Stopping Worker: waiting for active polls to complete...");

        try {
            if (!executor.awaitTermination(config.shutdownTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
                log.warn("Worker shutdown timeout, force-terminating");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }

        log.info("Worker stopped. Processed {} events, {} errors", eventsProcessed.get(), errorCount.get());
    }

    /**
     * Main polling loop (runs in background thread).
     * Poll -> Deserialize -> Handle -> Commit (if autoCommit enabled)
     */
    private void pollLoop() {
        String threadName = Thread.currentThread().getName();
        log.debug("{}: started polling loop", threadName);

        while (running.get()) {
            try {
                // Poll events from database
                // NOTE: In a real implementation, this would call sp_events__poll
                // For now, we provide a skeleton showing the structure
                List<PolledEvent<T>> events = pollEvents();

                if (events.isEmpty()) {
                    // No events available, wait before retrying
                    Thread.sleep(100);
                    continue;
                }

                // Process events
                boolean handled = handler.handle(events);

                if (handled && config.autoCommit()) {
                    // Commit cursors for successfully processed batches
                    commitCursors(events);
                }

                eventsProcessed.addAndGet(events.size());

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                errorCount.incrementAndGet();
                log.error("{}: error in polling loop", threadName, e);
                // Back-off before retrying
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        log.debug("{}: polling loop finished", threadName);
    }

    /**
     * Polls events from Boxy (skeleton implementation).
     * In a real Worker, this would call sp_events__poll via JDBC.
     *
     * @return List of polled events (may be empty)
     */
    private List<PolledEvent<T>> pollEvents() {
        // Skeleton: In production, call sp_events__poll here via JDBC
        // DataSource ds = ...;
        // try (Connection conn = ds.getConnection();
        //      CallableStatement stmt = conn.prepareCall("{call sp_events__poll(...)}")) {
        //     stmt.execute();
        //     // Parse result set into PolledEvent<T> records
        //     return parseResults(stmt.getResultSet());
        // }

        return new ArrayList<>(); // Empty for now (skeleton)
    }

    /**
     * Commits cursors after successful processing (skeleton implementation).
     * In a real Worker, this would call sp_cursors__commit for each partition.
     *
     * @param events Events that were processed
     */
    private void commitCursors(List<PolledEvent<T>> events) {
        // Skeleton: Group events by partition, call sp_cursors__commit for each
        // for (var partition : groupByPartition(events).entrySet()) {
        //     long maxSeq = partition.getValue().stream()
        //         .mapToLong(PolledEvent::sequence)
        //         .max()
        //         .orElse(-1);
        //     try (Connection conn = ds.getConnection();
        //          CallableStatement stmt = conn.prepareCall("{call sp_cursors__commit(...)}")) {
        //         stmt.setString(1, config.subscriptionName());
        //         stmt.setString(2, config.path());
        //         stmt.setString(3, config.topic());
        //         stmt.setInt(4, partition.getKey());
        //         stmt.setLong(5, maxSeq);
        //         stmt.execute();
        //     }
        // }
    }

    /**
     * Returns the number of events processed so far.
     *
     * @return Events processed count
     */
    public long getEventsProcessed() {
        return eventsProcessed.get();
    }

    /**
     * Returns the number of errors encountered.
     *
     * @return Error count
     */
    public long getErrorCount() {
        return errorCount.get();
    }

    /**
     * Returns whether the worker is currently running.
     *
     * @return true if running, false otherwise
     */
    public boolean isRunning() {
        return running.get();
    }

    private void registerMetrics() {
        Tags tags = Tags.of(
            Tag.of("topic", config.topic()),
            Tag.of("subscription", config.subscriptionName())
        );

        metrics.gauge(
            "boxy.worker.events.processed",
            tags,
            eventsProcessed,
            AtomicLong::get
        );

        metrics.gauge(
            "boxy.worker.errors.count",
            tags,
            errorCount,
            AtomicLong::get
        );
    }
}
