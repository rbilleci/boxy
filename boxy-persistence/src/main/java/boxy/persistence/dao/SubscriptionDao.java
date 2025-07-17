package boxy.persistence.dao;

import boxy.persistence.model.Subscription;
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.transaction.Transaction;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static java.lang.String.format;

@RegisterConstructorMapper(Subscription.class)
public interface SubscriptionDao extends SqlObject {

    @CreateSqlObject
    ConsumerGroupDao consumerGroupDao();

    @CreateSqlObject
    TopicDao topicDao();

    @SqlQuery("SELECT * FROM subscriptions WHERE id = :id")
    Optional<Subscription> find(@Bind("id") long id);

    @SqlQuery("SELECT * FROM subscriptions WHERE consumer_group_id = :consumerGroupId AND topic_id = :topicId")
    Optional<Subscription> find(@Bind("consumerGroupId") long consumerGroupId, @Bind("topicId") long topicId);

    default Optional<Subscription> find(String tenant, String consumerGroupName, String topic) {
        return find(resolveConsumerGroupId(tenant, consumerGroupName), resolveTopicId(tenant, topic));
    }

    @SqlQuery("SELECT * FROM subscriptions ORDER BY id LIMIT :limit OFFSET :offset")
    List<Subscription> findAll(@Bind("limit") int limit, @Bind("offset") int offset);


    @Transaction
    default long subscribe(String tenant, String consumerGroupName, String topic) {
        return subscribe(resolveConsumerGroupId(tenant, consumerGroupName), resolveTopicId(tenant, topic));
    }

    @Transaction
    default void subscribe(String tenant, String consumerGroupName, String... topics) {
        final var consumerGroupId = resolveConsumerGroupId(tenant, consumerGroupName);
        for (final var topic : topics) {
            subscribe(consumerGroupId, resolveTopicId(tenant, topic));
        }
    }

    @Transaction
    default long subscribe(long consumerGroupId, long topicId) {
        final var h = getHandle();
        final var subscriptionId = h.createUpdate("INSERT INTO subscriptions (consumer_group_id, topic_id) VALUES (:consumerGroupId, :topicId)")
                .bind("consumerGroupId", consumerGroupId)
                .bind("topicId", topicId)
                .executeAndReturnGeneratedKeys("id")
                .mapTo(Long.class)
                .one();
        h.createUpdate("""
                        INSERT INTO subscription_offsets (subscription_id, partition_id, committed_offset)
                        SELECT :subscriptionId, id, 0 FROM partitions WHERE topic_id = :topicId
                        """)
                .bind("subscriptionId", subscriptionId)
                .bind("topicId", topicId)
                .execute();
        return subscriptionId;
    }

    @Transaction
    default void unsubscribe(String tenant, String consumerGroupName, String topic) {
        unsubscribe(resolveSubscriptionId(tenant, consumerGroupName, topic));
    }

    @Transaction
    default void unsubscribe(String tenant, String consumerGroupName, String... topics) {
        final var consumerGroupId = resolveConsumerGroupId(tenant, consumerGroupName);
        for (final var topic : topics) {
            unsubscribe(consumerGroupId, resolveTopicId(tenant, topic));
        }
    }

    @Transaction
    default void unsubscribe(long consumerGroupId, long topicId) {
        unsubscribe(resolveSubscriptionId(consumerGroupId, topicId));
    }

    @Transaction
    @SqlUpdate("DELETE FROM subscriptions WHERE id = :id")
    void unsubscribe(@Bind("id") long id);


    private long resolveSubscriptionId(String tenant, String consumerGroupName, String topic) {
        return resolveSubscriptionId(
                resolveConsumerGroupId(tenant, consumerGroupName),
                resolveTopicId(tenant, topic));
    }

    private long resolveSubscriptionId(long consumerGroupId, long topicId) {
        return find(consumerGroupId, topicId)
                .orElseThrow(() -> new NoSuchElementException(format("Subscription %s.%s not found",
                        consumerGroupId,
                        topicId)))
                .id();
    }

    private long resolveConsumerGroupId(String tenant, String consumerGroupName) {
        return consumerGroupDao().find(tenant, consumerGroupName)
                .orElseThrow(() -> new NoSuchElementException(format("Consumer Group %s.%s not found",
                        tenant,
                        consumerGroupName)))
                .id();
    }

    private long resolveTopicId(String tenant, String topic) {
        return topicDao().find(tenant, topic)
                .orElseThrow(() -> new NoSuchElementException(format("Topic %s.%s not found",
                        tenant,
                        topic)))
                .id();
    }
}
