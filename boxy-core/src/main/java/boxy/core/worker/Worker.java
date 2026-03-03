package boxy.core.worker;

import boxy.core.util.JsonUtils;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Async worker for consuming events from Boxy topics.
 *
 * <p>The Worker polls events in a background thread pool, processes them via
 * an {@link EventHandler} callback, and optionally commits cursors. Supports:
 * <ul>
 *   <li>Multiple concurrent pollers (partition-level parallelism)</li>
 *   <li>Back-pressure via configurable poll timeout</li>
 *   <li>Graceful shutdown with timeout</li>
 *   <li>Consumer lifecycle management (register/deregister)</li>
 *   <li>Metrics (events processed, errors, poll latency)</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>
 * var config = WorkerConfig.builder()
 *     .subscriptionName("my-group")
 *     .path("/tenant-a/payments")
 *     .topic("orders")
 *     .maxBatchSize(50)
 *     .maxConcurrentPollers(4)
 *     .build();
 *
 * var worker = new Worker&lt;&gt;(
 *     config,
 *     dataSource,
 *     events -&gt; {
 *         for (var event : events) {
 *             System.out.println("Event: " + event.payload());
 *         }
 *         return true; // commit
 *     },
 *     payload -&gt; payload,  // payload deserializer (identity for String)
 *     meterRegistry
 * );
 *
 * worker.start();
 * // ... process events ...
 * worker.stop();
 * </pre>
 *
 * <p>The worker registers a consumer with a unique UUID on {@link #start()} and deregisters
 * it on {@link #stop()}. Each concurrent poller shares the same consumer ID and calls
 * {@code sp_events__poll} to fetch batches of events. After successful processing,
 * cursors are committed via {@code sp_cursors__commit}.
 *
 * @param <T> Event payload type
 * @since 1.0
 */
public class Worker<T> {
    private static final Logger log = LoggerFactory.getLogger(Worker.class);

    private final WorkerConfig config;
    private final DataSource dataSource;
    private final EventHandler<T> handler;
    private final Function<String, T> deserializer;
    private final MeterRegistry metrics;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService executor;
    private final AtomicLong eventsProcessed = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);

    /** Consumer ID assigned on start; used for poll and commit calls. */
    private volatile String consumerId;

    /**
     * Creates a new Worker.
     *
     * @param config       Worker configuration (subscription, topic, batch size, etc.)
     * @param dataSource   JDBC data source for database access
     * @param handler      Event handler callback invoked for each polled batch
     * @param deserializer Function to deserialize the raw payload string into type T
     * @param metrics      Meter registry for observability (optional, can be null)
     */
    public Worker(
        WorkerConfig config,
        DataSource dataSource,
        EventHandler<T> handler,
        Function<String, T> deserializer,
        MeterRegistry metrics
    ) {
        if (config == null) throw new IllegalArgumentException("config must not be null");
        if (dataSource == null) throw new IllegalArgumentException("dataSource must not be null");
        if (handler == null) throw new IllegalArgumentException("handler must not be null");
        if (deserializer == null) throw new IllegalArgumentException("deserializer must not be null");

        this.config = config;
        this.dataSource = dataSource;
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
     * Starts the worker: registers a consumer and begins polling in background threads.
     *
     * <p>Generates a unique consumer ID (UUIDv4), registers it with the subscription
     * via {@code sp_consumers__register}, then spawns concurrent polling threads.
     *
     * @throws RuntimeException if consumer registration fails
     */
    public void start() {
        if (!running.compareAndSet(false, true)) {
            log.warn("Worker already running for {}", config.topic());
            return;
        }

        // Generate unique consumer ID and register
        this.consumerId = UUID.randomUUID().toString();

        log.info(
            "Starting Worker: subscription={}, topic={}, path={}, consumerId={}, pollers={}",
            config.subscriptionName(), config.topic(), config.path(),
            consumerId, config.maxConcurrentPollers()
        );

        try {
            registerConsumer();
        } catch (Exception e) {
            running.set(false);
            throw new RuntimeException("Failed to register consumer", e);
        }

        // Spawn concurrent pollers
        for (int i = 0; i < config.maxConcurrentPollers(); i++) {
            executor.submit(this::pollLoop);
        }
    }

    /**
     * Stops the worker gracefully: signals polling threads to stop, waits for completion,
     * then deregisters the consumer.
     */
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            log.warn("Worker not running");
            return;
        }

        log.info("Stopping Worker: waiting for active polls to complete...");

        executor.shutdown(); // Signal no new tasks accepted

        try {
            if (!executor.awaitTermination(config.shutdownTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
                log.warn("Worker shutdown timeout, force-terminating");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }

        // Deregister consumer
        try {
            deregisterConsumer();
        } catch (Exception e) {
            log.warn("Failed to deregister consumer {}: {}", consumerId, e.getMessage());
        }

        log.info("Worker stopped. Processed {} events, {} errors", eventsProcessed.get(), errorCount.get());
    }

    /**
     * Main polling loop (runs in background thread).
     * Poll → Deserialize → Handle → Commit (if autoCommit enabled)
     */
    private void pollLoop() {
        String threadName = Thread.currentThread().getName();
        log.debug("{}: started polling loop", threadName);

        while (running.get()) {
            try {
                List<PolledEvent<T>> events = pollEvents();

                if (events.isEmpty()) {
                    // No events available; sleep for configurable poll timeout
                    Thread.sleep(config.pollTimeout().toMillis());
                    continue;
                }

                // Process events via handler
                boolean handled = handler.handle(events);

                if (handled && config.autoCommit()) {
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
     * Registers the consumer with Boxy via {@code sp_consumers__register}.
     *
     * <p>The stored procedure expects:
     * <ol>
     *   <li>{@code p_consumer_id} — UUIDv4 string</li>
     *   <li>{@code p_subscription_name} — subscription name</li>
     *   <li>{@code p_topics} — JSON array of topic paths (e.g. {@code ["/tenant/topic"]})</li>
     * </ol>
     */
    private void registerConsumer() {
        String topicPath = config.path() + "/" + config.topic();
        String topicsJson = JsonUtils.jsonArray(List.of(topicPath));

        log.debug("Registering consumer: id={}, subscription={}, topics={}",
                consumerId, config.subscriptionName(), topicsJson);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("{CALL sp_consumers__register(?, ?, ?)}")) {
            stmt.setString(1, consumerId);
            stmt.setString(2, config.subscriptionName());
            stmt.setString(3, topicsJson);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to register consumer: " + e.getMessage(), e);
        }
    }

    /**
     * Deregisters the consumer via {@code sp_consumers__deregister}.
     */
    private void deregisterConsumer() {
        if (consumerId == null) return;

        log.debug("Deregistering consumer: id={}", consumerId);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("{CALL sp_consumers__deregister(?)}")) {
            stmt.setString(1, consumerId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.warn("Failed to deregister consumer {}: sqlState={} message='{}'",
                    consumerId, e.getSQLState(), e.getMessage());
        }
    }

    /**
     * Polls events from Boxy via {@code sp_events__poll}.
     *
     * <p>The stored procedure returns two result sets:
     * <ol>
     *   <li>Events: cursor_id, partition_id, sequence, event_id, data</li>
     *   <li>Metadata: polling_probability (used for adaptive polling)</li>
     * </ol>
     *
     * <p>Each raw {@code data} column (LONGBLOB) is converted to a String and then
     * deserialized via the configured deserializer function.
     *
     * @return List of polled events (may be empty if no events available)
     */
    private List<PolledEvent<T>> pollEvents() {
        List<PolledEvent<T>> events = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("{CALL sp_events__poll(?, ?)}")) {

            stmt.setString(1, consumerId);
            stmt.setInt(2, config.maxBatchSize());

            boolean hasResultSet = stmt.execute();

            if (hasResultSet) {
                try (ResultSet rs = stmt.getResultSet()) {
                    while (rs.next()) {
                        long cursorId = rs.getLong("cursor_id");
                        long partitionId = rs.getLong("partition_id");
                        long sequence = rs.getLong("sequence");
                        long eventId = rs.getLong("event_id");
                        byte[] rawData = rs.getBytes("data");

                        // Deserialize payload: LONGBLOB → String → T
                        String payloadStr = rawData != null
                                ? new String(rawData, java.nio.charset.StandardCharsets.UTF_8)
                                : "";
                        T payload = deserializer.apply(payloadStr);

                        events.add(new PolledEvent<>(
                            eventId,
                            cursorId,      // We track cursor_id for commit grouping
                            (int) partitionId,
                            sequence,
                            null,          // eventKey not returned by sp_events__poll
                            payload,
                            System.currentTimeMillis()  // Use current time as proxy
                        ));
                    }
                }
            }

            if (metrics != null && events.size() > 0) {
                Timer.builder("boxy.worker.poll.latency")
                    .tags(Tags.of(
                        Tag.of("topic", config.topic()),
                        Tag.of("subscription", config.subscriptionName())
                    ))
                    .register(metrics)
                    .record(java.time.Duration.ZERO); // Record poll occurrence
            }

        } catch (SQLException e) {
            throw new RuntimeException("Poll failed: " + e.getMessage(), e);
        }

        return events;
    }

    /**
     * Commits cursors after successful event processing via {@code sp_cursors__commit}.
     *
     * <p>Groups events by cursor_id and computes the maximum sequence per cursor.
     * The stored procedure expects a JSON object mapping cursor IDs to positions:
     * {@code {"123": 456, "789": 1011}}.
     *
     * @param events The events that were successfully processed
     */
    private void commitCursors(List<PolledEvent<T>> events) {
        // Group by cursor_id, compute max sequence per cursor
        Map<Long, Long> cursorPositions = new HashMap<>();
        for (PolledEvent<T> event : events) {
            cursorPositions.merge(event.cursorId(), event.sequence(), Math::max);
        }

        if (cursorPositions.isEmpty()) return;

        String positionsJson = JsonUtils.jsonObject(cursorPositions);

        log.debug("Committing cursors: consumerId={}, positions={}", consumerId, positionsJson);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("{CALL sp_cursors__commit(?, ?)}")) {
            stmt.setString(1, consumerId);
            stmt.setString(2, positionsJson);
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.error("Cursor commit failed: consumerId={}, sqlState={}, message='{}'",
                    consumerId, e.getSQLState(), e.getMessage());
            throw new RuntimeException("Cursor commit failed: " + e.getMessage(), e);
        }
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

    /**
     * Returns the consumer ID assigned to this worker (null if not started).
     *
     * @return Consumer UUID string, or null if the worker has not been started
     */
    public String getConsumerId() {
        return consumerId;
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
