package boxy.persistence.service;

import boxy.persistence.dao.EventDao;
import boxy.persistence.dao.PartitionDao;
import boxy.persistence.dao.TopicDao;
import boxy.persistence.model.Partition;
import boxy.persistence.model.Topic;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.jdbi.v3.core.Jdbi;

import java.util.List;
import java.util.NoSuchElementException;

public class EventService {

    private record TopicKey(String tenant, String name) {
    }

    private record PartitionKey(long topicId, int partitionNumber) {
    }

    private final EventDao eventDao;
    private final TopicDao topicDao;
    private final PartitionDao partitionDao;
    private final Cache<TopicKey, Topic> topicCache = Caffeine.newBuilder().maximumSize(1000).build();
    private final Cache<PartitionKey, Partition> partitionCache = Caffeine.newBuilder().maximumSize(1000).build();

    public EventService(Jdbi jdbi) {
        this.eventDao = jdbi.onDemand(EventDao.class);
        this.topicDao = jdbi.onDemand(TopicDao.class);
        this.partitionDao = jdbi.onDemand(PartitionDao.class);
    }

    public void publishAdvanced(String tenant, String topicName, String key, String data) {
        final var topic = resolveTopic(tenant, topicName);
        final var partitionNumber = Math.floorMod(key.hashCode(), topic.partitions());
        final var partition = resolvePartition(topic.id(), partitionNumber);
        eventDao.publishAdvanced(partition.id(), data);
    }

    public void publish(String tenant, String topicName, String key, String data) {
        eventDao.publish(tenant, topicName, key, data);
    }

    public void publishMulti(String tenant, String topicName, String key, List<String> data) {
        final var topic = resolveTopic(tenant, topicName);
        final var partitionNumber = Math.floorMod(key.hashCode(), topic.partitions());
        final var partition = resolvePartition(topic.id(), partitionNumber);
        eventDao.publishMulti(partition.id(), data);
    }

    private Topic resolveTopic(String tenant, String name) {
        final var key = new TopicKey(tenant, name);
        return topicCache.get(key, k ->
                topicDao.find(k.tenant(), k.name())
                        .orElseThrow(() -> new NoSuchElementException("topic " + k.tenant() + "." + k.name() + " not found")));
    }

    private Partition resolvePartition(long topicId, int partitionNumber) {
        final var key = new PartitionKey(topicId, partitionNumber);
        return partitionCache.get(key, k ->
                partitionDao.find(k.topicId(), k.partitionNumber())
                        .orElseThrow(() -> new NoSuchElementException("partition " + k.topicId() + "." + k.partitionNumber() + " not found")));
    }

}
