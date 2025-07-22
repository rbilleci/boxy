package boxy.persistence.service;

import boxy.persistence.dao.ConsumerGroupDao;
import boxy.persistence.dao.SubscriptionDao;
import boxy.persistence.dao.TopicDao;
import boxy.persistence.model.Subscription;
import org.jdbi.v3.core.Jdbi;

import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.Optional;

public class SubscriptionService {

    private final SubscriptionDao subscriptionDao;
    private final TopicDao topicDao;
    private final ConsumerGroupDao consumerGroupDao;

    public SubscriptionService(Jdbi jdbi) {
        this.subscriptionDao = jdbi.onDemand(SubscriptionDao.class);
        this.topicDao = jdbi.onDemand(TopicDao.class);
        this.consumerGroupDao = jdbi.onDemand(ConsumerGroupDao.class);
    }

    public Optional<Subscription> find(String tenant, String consumerGroupName, String topic) {
        final var consumerGroupId = consumerGroupId(tenant, consumerGroupName);
        final var topicId = topicId(tenant, topic);
        return subscriptionDao.find(consumerGroupId, topicId);
    }

    public long subscribe(String tenant, String consumerGroupName, String topic) {
        final var consumerGroupId = consumerGroupId(tenant, consumerGroupName);
        final var topicId = topicId(tenant, topic);
        return subscriptionDao.subscribe(consumerGroupId, topicId);
    }

    public void subscribe(String tenant, String consumerGroupName, String... topics) {
        final var consumerGroupId = consumerGroupId(tenant, consumerGroupName);
        final var topicIds = Arrays.stream(topics).map(topic -> topicId(tenant, topic)).toList();
        subscriptionDao.subscribe(consumerGroupId, topicIds);
    }

    public void unsubscribe(String tenant, String consumerGroupName, String... topics) {
        final var consumerGroupId = consumerGroupId(tenant, consumerGroupName);
        final var topicIds = Arrays.stream(topics).map(topic -> topicId(tenant, topic)).toList();
        subscriptionDao.unsubscribe(consumerGroupId, topicIds);
    }

    private long consumerGroupId(String tenant, String consumerGroupName) {
        return consumerGroupDao.find(tenant, consumerGroupName)
                .orElseThrow(() -> new NoSuchElementException("Consumer Group " + tenant + "." + consumerGroupName + " not found"))
                .id();
    }

    private long topicId(String tenant, String topic) {
        return topicDao.find(tenant, topic)
                .orElseThrow(() -> new NoSuchElementException("Topic " + tenant + "." + topic + " not found"))
                .id();
    }
}
