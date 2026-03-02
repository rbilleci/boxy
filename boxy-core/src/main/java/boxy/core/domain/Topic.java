package boxy.core.domain;

import java.time.Instant;

/**
 * Represents a topic within a namespace.
 *
 * <p>Topics are the fundamental unit of event streaming. Each topic has one or more
 * partitions for scalability. Events are published to topics and consumed via subscriptions.
 *
 * @param id Unique topic identifier (auto-generated)
 * @param namespaceId Parent namespace ID
 * @param name Topic name (must be unique within the namespace)
 * @param partitions Number of partitions for this topic
 * @param createdAt When the topic was created
 * @param lastModifiedAt When the topic was last updated
 */
public record Topic(
        long id,
        long namespaceId,
        String name,
        int partitions,
        Instant createdAt,
        Instant lastModifiedAt) {
}
