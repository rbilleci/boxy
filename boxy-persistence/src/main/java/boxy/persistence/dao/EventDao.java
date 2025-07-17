package boxy.persistence.dao;

import boxy.persistence.model.Partition;
import boxy.persistence.model.Topic;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.jdbi.v3.sqlobject.SqlObject;

import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;

import static java.lang.String.format;

public interface EventDao extends SqlObject {

    record TopicKey(String tenant, String name) {
    }

    record PartitionKey(long topicId, int partitionNumber) {
    }

    @CreateSqlObject
    TopicDao topicDao();

    @CreateSqlObject
    PartitionDao partitionDao();

    Cache<TopicKey, Topic> TOPIC_CACHE = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(5))
            .maximumSize(1000)
            .build();

    Cache<PartitionKey, Partition> PARTITION_CACHE = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(60))
            .maximumSize(1000)
            .build();


    default void publish(String tenant, String topicName, String key, String data) {
        final var topic = resolveTopic(tenant, topicName);
        final var partitionNumber = Math.floorMod(key.hashCode(), topic.partitions());
        final var partition = resolvePartition(topic.id(), partitionNumber);
        publish(partition.id(), data);
    }

    default void publish(long partitionId, String data) {
        final var h = getHandle();
        h.createUpdate("INSERT INTO events (partition_id, data) VALUES (?, ?)")
                .bind(0, partitionId)
                .bind(1, data)
                .execute();
    }

    default void publish(String tenant, String topicName, String key, List<String> data) {
        final var topic = resolveTopic(tenant, topicName);
        final var partitionNumber = Math.floorMod(key.hashCode(), topic.partitions());
        final var partition = resolvePartition(topic.id(), partitionNumber);
        publish(partition.id(), data);
    }

    default void publish(long partitionId, List<String> data) {
        final var h = getHandle();
        final var b = h.prepareBatch("INSERT INTO events (partition_id, data) VALUES (?, ?)");
        data.forEach(d -> b.bind(0, partitionId).bind(1, d));
        b.execute();
    }

    private Topic resolveTopic(final String tenant, final String name) {
        final var key = new TopicKey(tenant, name);
        return TOPIC_CACHE.get(
                key,
                (_) -> topicDao()
                        .find(tenant, name)
                        .orElseThrow(() -> new NoSuchElementException(format("topic %s.%s not found)", tenant, name))));
    }

    private Partition resolvePartition(final long topicId, final int partitionNumber) {
        final var key = new PartitionKey(topicId, partitionNumber);
        return PARTITION_CACHE.get(
                key,
                (_) -> partitionDao()
                        .find(topicId, partitionNumber)
                        .orElseThrow(() -> new NoSuchElementException(format("partition %s.%s not found)", topicId, partitionNumber))));
    }
}
