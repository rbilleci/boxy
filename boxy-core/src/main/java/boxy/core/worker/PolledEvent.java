package boxy.core.worker;

/**
 * Represents a single event polled from Boxy.
 *
 * <p>Returned by the Worker's internal poll mechanism after calling
 * {@code sp_events__poll}. The {@code cursorId} and {@code sequence}
 * fields are used internally by the Worker to commit cursor positions
 * after successful processing.
 *
 * @param <T> Payload type (e.g., String, byte[], custom record)
 * @since 1.0
 */
public record PolledEvent<T>(
    long id,                 // Event ID (unique within topic)
    long cursorId,           // Cursor ID (used for commit grouping)
    int partitionId,         // Partition (0 to partitions-1)
    long sequence,           // Sequence within partition (strictly increasing)
    String eventKey,         // Routing key for the event (may be null)
    T payload,               // Deserialized payload
    long publishedAtMs       // Timestamp when event was published
) {
    /**
     * Constructor for PolledEvent.
     *
     * @param id Event ID
     * @param cursorId Cursor ID (used internally for commit tracking)
     * @param partitionId Partition number
     * @param sequence Sequence within partition
     * @param eventKey Routing key (may be null)
     * @param payload Deserialized payload
     * @param publishedAtMs Publication timestamp in milliseconds
     */
    public PolledEvent {}
}
