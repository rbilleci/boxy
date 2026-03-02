package boxy.core.domain;

import java.time.Instant;

/**
 * Links a subscription to a topic, tracking topic-specific consumer metadata.
 *
 * <p>SubscriptionTopics track active consumer count and heartbeat interval per topic
 * within a subscription. This allows independent heartbeat intervals for different topics
 * and enables efficient adaptive polling probability computation.
 *
 * @param id Unique subscription_topic record ID (auto-generated)
 * @param subscriptionId The subscription
 * @param topicId The subscribed topic
 * @param heartbeatInterval Heartbeat interval for consumers on this subscription/topic
 * @param activePartitions Number of partitions in this topic
 * @param activeConsumers Number of active (non-expired) consumers reading this topic
 * @param createdAt When the subscription was created
 * @param lastModifiedAt When the subscription was last updated
 */
public record SubscriptionTopic(
        long id,
        long subscriptionId,
        long topicId,
        double heartbeatInterval,
        int activePartitions,
        int activeConsumers,
        Instant createdAt,
        Instant lastModifiedAt) {
}
