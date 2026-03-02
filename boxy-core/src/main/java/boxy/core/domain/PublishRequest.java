package boxy.core.domain;

/**
 * Represents a single event to be published via the batch publish path.
 *
 * <p>Used as the element type for {@code EventRepository.publishBatch(List)},
 * which serialises a list of these records into the JSON array expected by
 * {@code sp_events__publish_multi}.
 *
 * @param path  namespace path (e.g. {@code "tenant-a/payments"})
 * @param topic topic name     (e.g. {@code "orders"})
 * @param key   routing key used for CRC32-based partition selection
 * @param data  event payload  (typically a JSON string)
 */
public record PublishRequest(
        String path,
        String topic,
        String key,
        String data) {
}
