package boxy.core.it;

import boxy.core.repository.*;
import boxy.core.domain.*;

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
        final var topicRepository = new TopicRepository(ds);
        final var consumerGroupRepository = new ConsumerGroupRepository(ds);
        final var subscriptionRepository = new SubscriptionRepository(ds);
        final var subscriptionOffsetRepository = new SubscriptionOffsetRepository(ds);
        final var workerRepository = new WorkerRepository(ds);

        // Create the topics
        topicRepository.create(TENANT_1, TOPIC_A, DEFAULT_PARTITIONS);
        topicRepository.create(TENANT_1, TOPIC_B, DEFAULT_PARTITIONS);
        topicRepository.create(TENANT_2, TOPIC_C, DEFAULT_PARTITIONS);
        topicRepository.create(TENANT_2, TOPIC_D, DEFAULT_PARTITIONS);
        final var topicA = topicRepository.find(TENANT_1, TOPIC_A).orElseThrow();

        // Create the consumer groups
        consumerGroupRepository.create(TENANT_1, CONSUMER_GROUP_A);
        consumerGroupRepository.create(TENANT_1, CONSUMER_GROUP_B);
        consumerGroupRepository.create(TENANT_1, CONSUMER_GROUP_C);
        consumerGroupRepository.create(TENANT_1, CONSUMER_GROUP_D);
        consumerGroupRepository.create(TENANT_2, CONSUMER_GROUP_A);
        consumerGroupRepository.create(TENANT_2, CONSUMER_GROUP_B);
        consumerGroupRepository.create(TENANT_2, CONSUMER_GROUP_C);
        consumerGroupRepository.create(TENANT_2, CONSUMER_GROUP_D);
        final var consumerGroups = new ArrayList<>(consumerGroupRepository.findAll(100, 0));

        // SUBSCRIBE TENANT 1
        subscriptionRepository.subscribe(TENANT_1, CONSUMER_GROUP_A, TOPIC_A);
        subscriptionRepository.subscribe(TENANT_1, CONSUMER_GROUP_A, TOPIC_B);
        subscriptionRepository.subscribe(TENANT_1, CONSUMER_GROUP_B, TOPIC_A);
        subscriptionRepository.subscribe(TENANT_1, CONSUMER_GROUP_B, TOPIC_B);
        // SUBSCRIBE TENANT 2
        subscriptionRepository.subscribe(TENANT_2, CONSUMER_GROUP_A, TOPIC_C);
        subscriptionRepository.subscribe(TENANT_2, CONSUMER_GROUP_A, TOPIC_D);
        subscriptionRepository.subscribe(TENANT_2, CONSUMER_GROUP_B, TOPIC_C);
        subscriptionRepository.subscribe(TENANT_2, CONSUMER_GROUP_B, TOPIC_D);


        final var subscriptions = new SubscriptionRepository(ds).findAll(100, 0);
        final var firstSubscription = subscriptions.getFirst();
        final var subscriptionOffset = subscriptionOffsetRepository.findAll(firstSubscription.id()).getFirst();

        final var result1 = workerRepository.checkIn(PARTY_1, consumerGroups.get(0).id(), 1);
        final var result2 = workerRepository.checkIn(PARTY_2, consumerGroups.get(1).id(), 1);
        final var worker1 = workerRepository.find(PARTY_1).orElseThrow();
        final var worker2 = workerRepository.find(PARTY_2).orElseThrow();

        return new TestData(
                topicA,
                consumerGroups,
                subscriptions,
                subscriptionOffset,
                worker1,
                worker2);

    }
}

