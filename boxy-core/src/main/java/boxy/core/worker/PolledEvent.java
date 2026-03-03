package boxy.core.worker;

/**
 * Represents a single event polled from Boxy.
 *
 * @param <T> Payload type (e.g., String, byte[], custom record)
 * @since 1.0
 */
public record PolledEvent<T>(
    long id,                 // Event ID (unique within topic)
    long topicId,           // Topic ID
    int partitionId,        // Partition (0 to partitions-1)
    long sequence,          // Sequence within partition (strictly increasing)
    String eventKey,        // Routing key for the event
    T payload,              // Deserialized payload
    long publishedAtMs      // Timestamp when event was published
) {
    /**
     * Constructor for PolledEvent.
     *
     * @param id Event ID
     * @param topicId Topic ID
     * @param partitionId Partition number
     * @param sequence Sequence within partition
     * @param eventKey Routing key
     * @param payload Deserialized payload
     * @param publishedAtMs Publication timestamp in milliseconds
     */
    public PolledEvent {}
}
