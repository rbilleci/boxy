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
                       List<ConsumerGroup> consumerGroups,
                       Cursor cursor,
                       Consumer consumer1,
                       Consumer consumer2) {

    public static final int DEFAULT_PARTITIONS = 16;
    public static final String CONSUMER_GROUP_A = "sub-a";
    public static final String CONSUMER_GROUP_B = "sub-b";
    public static final String CONSUMER_GROUP_C = "sub-c";
    public static final String CONSUMER_GROUP_D = "sub-d";
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
        final var consumerGroupRepository = new ConsumerGroupRepository(ds);
        final var cursorRepository = new CursorRepository(ds);
        final var consumerRepository = new ConsumerRepository(ds);

        // Create the namespaces
        final var namespaceAId = namespaceRepository.create(PATH_A);
        final var namespaceBId = namespaceRepository.create(PATH_B);

        // Create the topics
        topicRepository.create(PATH_A, TOPIC_A, DEFAULT_PARTITIONS);
        topicRepository.create(PATH_A, TOPIC_B, DEFAULT_PARTITIONS);
        topicRepository.create(PATH_B, TOPIC_C, DEFAULT_PARTITIONS);
        topicRepository.create(PATH_B, TOPIC_D, DEFAULT_PARTITIONS);
        final var topicA = topicRepository.find(PATH_A, TOPIC_A).orElseThrow();

        // Create the consumer groups
        consumerGroupRepository.create(CONSUMER_GROUP_A);
        consumerGroupRepository.create(CONSUMER_GROUP_B);
        consumerGroupRepository.create(CONSUMER_GROUP_C);
        consumerGroupRepository.create(CONSUMER_GROUP_D);
        final var consumerGroups = new ArrayList<>(consumerGroupRepository.findAll(100, 0));

        // Subscribe topics
        consumerGroupRepository.subscribe(CONSUMER_GROUP_A, PATH_A, TOPIC_A);
        consumerGroupRepository.subscribe(CONSUMER_GROUP_A, PATH_A, TOPIC_B);
        consumerGroupRepository.subscribe(CONSUMER_GROUP_B, PATH_A, TOPIC_A);
        consumerGroupRepository.subscribe(CONSUMER_GROUP_B, PATH_A, TOPIC_B);

        consumerGroupRepository.subscribe(CONSUMER_GROUP_C, PATH_B, TOPIC_C);
        consumerGroupRepository.subscribe(CONSUMER_GROUP_C, PATH_B, TOPIC_D);
        consumerGroupRepository.subscribe(CONSUMER_GROUP_D, PATH_B, TOPIC_C);
        consumerGroupRepository.subscribe(CONSUMER_GROUP_D, PATH_B, TOPIC_D);

        final var cursor = cursorRepository.findAll(consumerGroups.getFirst().id()).getFirst();

        consumerRepository.checkIn(PARTY_1, consumerGroups.get(0).id(), 1);
        consumerRepository.checkIn(PARTY_2, consumerGroups.get(1).id(), 1);
        final var consumer1 = consumerRepository.find(PARTY_1).orElseThrow();
        final var consumer2 = consumerRepository.find(PARTY_2).orElseThrow();

        return new TestData(
                topicA,
                namespaceAId,
                namespaceBId,
                consumerGroups,
                cursor,
                consumer1,
                consumer2);
    }
}
