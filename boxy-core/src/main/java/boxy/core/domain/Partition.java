package boxy.core.domain;

/**
 * Represents a partition within a topic.
 *
 * <p>Partitions are the unit of parallelism. Events are distributed across partitions
 * based on a routing key (CRC32 hash). The high_watermark tracks the highest sequence
 * number assigned to this partition.
 *
 * @param id Encoded partition identifier ((topic_id << 16) + partition_number)
 * @param topicId Parent topic ID
 * @param partitionNumber Partition index within the topic (0-based)
 * @param highWatermark Highest sequence number assigned to this partition
 */
public record Partition(
        long id,
        long topicId,
        int partitionNumber,
        long highWatermark) {
}
