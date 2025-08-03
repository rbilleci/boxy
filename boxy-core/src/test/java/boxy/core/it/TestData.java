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
                       Cursor cursor,
                       Worker worker1,
                       Worker worker2) {

    public static final int DEFAULT_PARTITIONS = 16;
    public static final String SUBSCRIPTION_A = "sub-a";
    public static final String SUBSCRIPTION_B = "sub-b";
    public static final String SUBSCRIPTION_C = "sub-c";
    public static final String SUBSCRIPTION_D = "sub-d";
    public static final String PATH_A = "ns-a";
    public static final String PATH_B = "ns-a/ns-b";
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
        final var cursorRepository = new CursorRepository(ds);
        final var workerRepository = new WorkerRepository(ds);

        // Create the namespaces
        final var namespaceAId = namespaceRepository.create(PATH_A);
        final var namespaceBId = namespaceRepository.create(PATH_B);

        // Create the topics
        topicRepository.create(PATH_A, TOPIC_A, DEFAULT_PARTITIONS);
        topicRepository.create(PATH_A, TOPIC_B, DEFAULT_PARTITIONS);
        topicRepository.create(PATH_B, TOPIC_C, DEFAULT_PARTITIONS);
        topicRepository.create(PATH_B, TOPIC_D, DEFAULT_PARTITIONS);
        final var topicA = topicRepository.find(PATH_A, TOPIC_A).orElseThrow();

        // Create the subscriptions
        subscriptionRepository.create(SUBSCRIPTION_A);
        subscriptionRepository.create(SUBSCRIPTION_B);
        subscriptionRepository.create(SUBSCRIPTION_C);
        subscriptionRepository.create(SUBSCRIPTION_D);
        final var subscriptions = new ArrayList<>(subscriptionRepository.findAll(100, 0));

        // Subscribe topics
        subscriptionRepository.subscribe(SUBSCRIPTION_A, PATH_A, TOPIC_A);
        subscriptionRepository.subscribe(SUBSCRIPTION_A, PATH_A, TOPIC_B);
        subscriptionRepository.subscribe(SUBSCRIPTION_B, PATH_A, TOPIC_A);
        subscriptionRepository.subscribe(SUBSCRIPTION_B, PATH_A, TOPIC_B);

        subscriptionRepository.subscribe(SUBSCRIPTION_C, PATH_B, TOPIC_C);
        subscriptionRepository.subscribe(SUBSCRIPTION_C, PATH_B, TOPIC_D);
        subscriptionRepository.subscribe(SUBSCRIPTION_D, PATH_B, TOPIC_C);
        subscriptionRepository.subscribe(SUBSCRIPTION_D, PATH_B, TOPIC_D);

        final var cursor = cursorRepository.findAll(subscriptions.getFirst().id()).getFirst();

        workerRepository.checkIn(PARTY_1, subscriptions.get(0).id(), 1);
        workerRepository.checkIn(PARTY_2, subscriptions.get(1).id(), 1);
        final var worker1 = workerRepository.find(PARTY_1).orElseThrow();
        final var worker2 = workerRepository.find(PARTY_2).orElseThrow();

        return new TestData(
                topicA,
                namespaceAId,
                namespaceBId,
                subscriptions,
                cursor,
                worker1,
                worker2);
    }
}
