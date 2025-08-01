package boxy.core.it;

import boxy.core.repository.*;
import boxy.core.domain.*;

import javax.sql.DataSource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record TestData(Topic topic,
                       long namespaceAId,
                       long namespaceBId,
                       List<Subscription> subscriptions,
                       List<SubscriptionTopic> subscriptionTopics,
                       SubscriptionOffset subscriptionOffset,
                       Worker worker1,
                       Worker worker2) {

    public static final int DEFAULT_PARTITIONS = 16;
    public static final String SUBSCRIPTION_A = "sub-a";
    public static final String SUBSCRIPTION_B = "sub-b";
    public static final String SUBSCRIPTION_C = "sub-c";
    public static final String SUBSCRIPTION_D = "sub-d";
    public static final String TENANT_1 = "t-1";
    public static final String TENANT_2 = "t-2";
    public static final String NAMESPACE_A = "ns-a";
    public static final String NAMESPACE_B = "ns-b";
    public static final String TOPIC_A = "topic-a";
    public static final String TOPIC_B = "topic-b";
    public static final String TOPIC_C = "topic-c";
    public static final String TOPIC_D = "topic-d";
    public static final String PARTY_1 = UUID.randomUUID().toString();
    public static final String PARTY_2 = UUID.randomUUID().toString();

    public static TestData seed(final DataSource ds) {
        final var namespaceRepository = new NamespaceRepository(ds);
        final var topicRepository = new TopicRepository(ds);
        final var subscriptionRepository = new SubscriptionRepository(ds);
        final var subscriptionTopicRepository = new SubscriptionTopicRepository(ds);
        final var subscriptionOffsetRepository = new SubscriptionOffsetRepository(ds);
        final var workerRepository = new WorkerRepository(ds);

        // Create the namespaces
        final var namespaceAId = namespaceRepository.create(TENANT_1, NAMESPACE_A);
        final var namespaceBId = namespaceRepository.create(TENANT_2, NAMESPACE_B);

        // Create the topics
        topicRepository.create(TENANT_1, NAMESPACE_A, TOPIC_A, DEFAULT_PARTITIONS);
        topicRepository.create(TENANT_1, NAMESPACE_A, TOPIC_B, DEFAULT_PARTITIONS);
        topicRepository.create(TENANT_2, NAMESPACE_B, TOPIC_C, DEFAULT_PARTITIONS);
        topicRepository.create(TENANT_2, NAMESPACE_B, TOPIC_D, DEFAULT_PARTITIONS);
        final var topicA = topicRepository.find(TENANT_1, NAMESPACE_A, TOPIC_A).orElseThrow();

        // Create the subscriptions
        subscriptionRepository.create(TENANT_1, SUBSCRIPTION_A);
        subscriptionRepository.create(TENANT_1, SUBSCRIPTION_B);
        subscriptionRepository.create(TENANT_1, SUBSCRIPTION_C);
        subscriptionRepository.create(TENANT_1, SUBSCRIPTION_D);
        subscriptionRepository.create(TENANT_2, SUBSCRIPTION_A);
        subscriptionRepository.create(TENANT_2, SUBSCRIPTION_B);
        subscriptionRepository.create(TENANT_2, SUBSCRIPTION_C);
        subscriptionRepository.create(TENANT_2, SUBSCRIPTION_D);
        final var subscriptions = new ArrayList<>(subscriptionRepository.findAll(100, 0));

        // Subscribe topics
        subscriptionTopicRepository.subscribe(TENANT_1, SUBSCRIPTION_A, NAMESPACE_A, TOPIC_A);
        subscriptionTopicRepository.subscribe(TENANT_1, SUBSCRIPTION_A, NAMESPACE_A, TOPIC_B);
        subscriptionTopicRepository.subscribe(TENANT_1, SUBSCRIPTION_B, NAMESPACE_A, TOPIC_A);
        subscriptionTopicRepository.subscribe(TENANT_1, SUBSCRIPTION_B, NAMESPACE_A, TOPIC_B);

        subscriptionTopicRepository.subscribe(TENANT_2, SUBSCRIPTION_A, NAMESPACE_B, TOPIC_C);
        subscriptionTopicRepository.subscribe(TENANT_2, SUBSCRIPTION_A, NAMESPACE_B, TOPIC_D);
        subscriptionTopicRepository.subscribe(TENANT_2, SUBSCRIPTION_B, NAMESPACE_B, TOPIC_C);
        subscriptionTopicRepository.subscribe(TENANT_2, SUBSCRIPTION_B, NAMESPACE_B, TOPIC_D);

        final var subscriptionTopics = new SubscriptionTopicRepository(ds).findAll(100, 0);
        final var firstSubscriptionTopic = subscriptionTopics.getFirst();
        final var subscriptionOffset = subscriptionOffsetRepository.findAll(firstSubscriptionTopic.id()).getFirst();

        workerRepository.checkIn(PARTY_1, subscriptions.get(0).id(), 1);
        workerRepository.checkIn(PARTY_2, subscriptions.get(1).id(), 1);
        final var worker1 = workerRepository.find(PARTY_1).orElseThrow();
        final var worker2 = workerRepository.find(PARTY_2).orElseThrow();

        return new TestData(
                topicA,
                namespaceAId,
                namespaceBId,
                subscriptions,
                subscriptionTopics,
                subscriptionOffset,
                worker1,
                worker2);
    }
}
