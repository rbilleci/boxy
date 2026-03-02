package boxy.mysql.repository;

import boxy.core.domain.PublishRequest;
import boxy.core.repository.BaseRepository;

import javax.sql.DataSource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Repository for publishing events to Boxy topics.
 *
 * <p>Three publish paths are provided, in descending order of throughput:
 * <ol>
 *   <li>{@link #publishBatch(List)} — single JDBC call, single DB transaction,
 *       single bulk INSERT via {@code sp_events__publish_multi}.
 *       Best for producer batches of ≥ 10 events.</li>
 *   <li>{@link #publish(long, String)} — bypasses the topic cache; caller must
 *       resolve the partition ID. Use for hot-path, cache-aware producers.</li>
 *   <li>{@link #publish(String, String, String, String)} — single event with
 *       automatic topic cache lookup and CRC32 partition selection. Simplest
 *       API; highest per-event overhead.</li>
 * </ol>
 */
public final class EventRepository extends BaseRepository {

    public EventRepository(final DataSource ds) {
        super(ds);
    }

    /**
     * Publishes a single event via {@code sp_events__publish}, which performs a
     * topic-cache lookup (MD5 + MEMORY table) followed by CRC32 partition selection.
     *
     * <p>Each call is a separate JDBC round-trip and a separate DB transaction.
     * For high-throughput producers, prefer {@link #publishBatch(List)}.
     *
     * @param path  namespace path (e.g. {@code "tenant-a/payments"})
     * @param topic topic name     (e.g. {@code "orders"})
     * @param key   routing key for CRC32-based partition selection
     * @param data  event payload
     */
    public void publish(final String path, final String topic, final String key, final String data) {
        update("{CALL sp_events__publish(?,?,?,?)}", path, topic, key, data);
    }

    /**
     * Publishes a single event via {@code sp_events__publish_advanced}, bypassing
     * the topic cache.  The caller is responsible for resolving the correct
     * {@code partitionId} before calling this method.
     *
     * <p>This is the lowest-latency single-event publish path and should be used
     * by producers that cache partition metadata themselves.
     *
     * @param partitionId pre-resolved partition ID ({@code (topic_id << 16) + partition_number})
     * @param data        event payload
     */
    public void publish(final long partitionId, final String data) {
        update("{CALL sp_events__publish_advanced(?,?)}", partitionId, data);
    }

    /**
     * Publishes a batch of events in a single JDBC call and a single DB transaction
     * via {@code sp_events__publish_multi}.
     *
     * <p>The stored procedure serialises all events into one {@code INSERT INTO events}
     * and one {@code INSERT INTO unprocessed_events} statement, reducing per-event
     * overhead from O(N) round-trips to O(1).  For batch sizes ≥ 10, throughput is
     * expected to be 10–50× higher than calling {@link #publish(String, String, String, String)}
     * in a loop.
     *
     * @param events list of events to publish; must not be empty
     * @throws IllegalArgumentException if {@code events} is empty
     */
    public void publishBatch(final List<PublishRequest> events) {
        if (events.isEmpty()) {
            throw new IllegalArgumentException("publishBatch requires at least one event");
        }
        update("{CALL sp_events__publish_multi(?)}", toJsonArray(events));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Serialises a list of {@link PublishRequest} objects to the JSON array format
     * expected by {@code sp_events__publish_multi}.
     *
     * <p>Each field value is JSON-escaped to handle embedded quotes and backslashes.
     *
     * @param events events to serialise
     * @return JSON array string, e.g. {@code [{"path":"ns","topic":"t","key":"k","data":"..."}]}
     */
    private static String toJsonArray(final List<PublishRequest> events) {
        return events.stream()
                .map(e -> "{\"path\":"  + jsonString(e.path())  +
                          ",\"topic\":" + jsonString(e.topic()) +
                          ",\"key\":"   + jsonString(e.key())   +
                          ",\"data\":"  + jsonString(e.data())  + "}")
                .collect(Collectors.joining(",", "[", "]"));
    }

    /**
     * Wraps a string in JSON double-quotes, escaping any embedded backslashes
     * and double-quotes so the result is valid JSON.
     *
     * @param value raw string value (may be {@code null})
     * @return JSON string literal, or {@code "null"} if {@code value} is {@code null}
     */
    private static String jsonString(final String value) {
        if (value == null) return "null";
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
