package boxy.core.domain;

/**
 * Represents a consumer's read position cursor for a partition.
 *
 * <p>Cursors track the last successfully committed position per subscription/topic/partition
 * combination. Multiple consumers can own cursors for the same partition (via leases),
 * but only one can hold an active lease at a time.
 *
 * @param id Unique cursor identifier (auto-generated)
 * @param subscriptionId The subscription this cursor belongs to
 * @param subscriptionTopicId The subscription_topics entry linking subscription to topic
 * @param topicId The topic this cursor reads from
 * @param partitionId The partition this cursor reads from (encoded as (topic_id << 16) + partition_number)
 * @param randomKey Random integer used for load-balanced lease acquisition
 * @param position Current committed read position (sequence number) for this partition
 */
public record Cursor(
        long id,
        long subscriptionId,
        long subscriptionTopicId,
        long topicId,
        long partitionId,
        int randomKey,
        long position) {
}
