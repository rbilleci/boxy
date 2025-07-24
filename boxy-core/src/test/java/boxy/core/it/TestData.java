package boxy.core.it;

import boxy.core.dao.*;
import boxy.core.model.*;
import boxy.core.service.SubscriptionService;

import javax.sql.DataSource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record TestData(Topic topic,
                       List<ConsumerGroup> consumerGroups,
                       List<Subscription> subscriptions,
                       SubscriptionOffset subscriptionOffset,
                       Worker worker1,
                       Worker worker2) {

    public static final int DEFAULT_PARTITIONS = 16;
    public static final String CONSUMER_GROUP_A = "cg-a";
    public static final String CONSUMER_GROUP_B = "cg-b";
    public static final String CONSUMER_GROUP_C = "cg-c";
    public static final String CONSUMER_GROUP_D = "cg-d";
    public static final String TENANT_1 = "t-1";
    public static final String TENANT_2 = "t-2";
    public static final String TOPIC_A = "topic-a";
    public static final String TOPIC_B = "topic-b";
    public static final String TOPIC_C = "topic-c";
    public static final String TOPIC_D = "topic-d";
    public static final String PARTY_1 = UUID.randomUUID().toString();
    public static final String PARTY_2 = UUID.randomUUID().toString();

    public static TestData seed(final DataSource ds) {
        final var topicDao = new TopicDao(ds);
        final var consumerGroupDao = new ConsumerGroupDao(ds);
        final var subscriptionService = new SubscriptionService(ds);
        final var subscriptionOffsetDao = new SubscriptionOffsetDao(ds);
        final var workerDao = new WorkerDao(ds);

        // Create the topics
        final var topicA = topicDao.find(topicDao.create(TENANT_1, TOPIC_A, DEFAULT_PARTITIONS)).orElseThrow();
        final var topicB = topicDao.find(topicDao.create(TENANT_1, TOPIC_B, DEFAULT_PARTITIONS)).orElseThrow();
        final var topicC = topicDao.find(topicDao.create(TENANT_2, TOPIC_C, DEFAULT_PARTITIONS)).orElseThrow();
        final var topicD = topicDao.find(topicDao.create(TENANT_2, TOPIC_D, DEFAULT_PARTITIONS)).orElseThrow();


        // Create the consumer groups
        final var consumerGroups = new ArrayList<ConsumerGroup>();
        consumerGroups.add(consumerGroupDao.find(consumerGroupDao.create(TENANT_1, CONSUMER_GROUP_A)).orElseThrow());
        consumerGroups.add(consumerGroupDao.find(consumerGroupDao.create(TENANT_1, CONSUMER_GROUP_B)).orElseThrow());
        consumerGroups.add(consumerGroupDao.find(consumerGroupDao.create(TENANT_1, CONSUMER_GROUP_C)).orElseThrow());
        consumerGroups.add(consumerGroupDao.find(consumerGroupDao.create(TENANT_1, CONSUMER_GROUP_D)).orElseThrow());
        consumerGroups.add(consumerGroupDao.find(consumerGroupDao.create(TENANT_2, CONSUMER_GROUP_A)).orElseThrow());
        consumerGroups.add(consumerGroupDao.find(consumerGroupDao.create(TENANT_2, CONSUMER_GROUP_B)).orElseThrow());
        consumerGroups.add(consumerGroupDao.find(consumerGroupDao.create(TENANT_2, CONSUMER_GROUP_C)).orElseThrow());
        consumerGroups.add(consumerGroupDao.find(consumerGroupDao.create(TENANT_2, CONSUMER_GROUP_D)).orElseThrow());

        // SUBSCRIBE TENANT 1
        subscriptionService.subscribe(TENANT_1, CONSUMER_GROUP_A, TOPIC_A);
        subscriptionService.subscribe(TENANT_1, CONSUMER_GROUP_A, TOPIC_B);
        subscriptionService.subscribe(TENANT_1, CONSUMER_GROUP_B, TOPIC_A);
        subscriptionService.subscribe(TENANT_1, CONSUMER_GROUP_B, TOPIC_B);
        subscriptionService.subscribe(TENANT_2, CONSUMER_GROUP_A, TOPIC_C);
        subscriptionService.subscribe(TENANT_2, CONSUMER_GROUP_A, TOPIC_D);
        subscriptionService.subscribe(TENANT_2, CONSUMER_GROUP_B, TOPIC_C);
        subscriptionService.subscribe(TENANT_2, CONSUMER_GROUP_B, TOPIC_D);
        final var subscriptions = new SubscriptionDao(ds).findAll(100, 0);
        final var firstSubscription = subscriptions.getFirst();
        final var subscriptionOffset = subscriptionOffsetDao.findAll(firstSubscription.id()).getFirst();

        // Register workers
        final var worker1 = workerDao.find(workerDao.register(PARTY_1, consumerGroups.get(0).id(), 1)).orElseThrow();
        final var worker2 = workerDao.find(workerDao.register(PARTY_2, consumerGroups.get(1).id(), 1)).orElseThrow();

        return new TestData(
                topicA,
                consumerGroups,
                subscriptions,
                subscriptionOffset,
                worker1,
                worker2);

    }
}

