package boxy.persistence.dao;

import boxy.persistence.model.Subscription;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.transaction.Transaction;

import java.util.List;
import java.util.Optional;

@RegisterConstructorMapper(Subscription.class)
public interface SubscriptionDao extends SqlObject {

    @SqlQuery("SELECT * FROM subscriptions WHERE id = :id")
    Optional<Subscription> find(@Bind("id") long id);

    @SqlQuery("SELECT * FROM subscriptions WHERE consumer_group_id = :consumerGroupId AND topic_id = :topicId")
    Optional<Subscription> find(@Bind("consumerGroupId") long consumerGroupId, @Bind("topicId") long topicId);

    @SqlQuery("SELECT * FROM subscriptions ORDER BY id LIMIT :limit OFFSET :offset")
    List<Subscription> findAll(@Bind("limit") int limit, @Bind("offset") int offset);

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

    /** Subscribe to multiple topics atomically. */
    @Transaction
    default void subscribe(long consumerGroupId, long... topicIds) {
        for (long topicId : topicIds) {
            subscribe(consumerGroupId, topicId);
        }
    }

    @Transaction
    @SqlUpdate("DELETE FROM subscriptions WHERE id = :id")
    void unsubscribe(@Bind("id") long id);

    default void unsubscribe(long consumerGroupId, long topicId) {
        find(consumerGroupId, topicId).ifPresent(s -> unsubscribe(s.id()));
    }

    /** Unsubscribe from multiple topics atomically. */
    @Transaction
    default void unsubscribe(long consumerGroupId, long... topicIds) {
        for (long topicId : topicIds) {
            unsubscribe(consumerGroupId, topicId);
        }
    }
}
