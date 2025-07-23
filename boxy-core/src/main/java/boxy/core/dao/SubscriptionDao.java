package boxy.core.dao;

import boxy.core.model.Subscription;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

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

    default long subscribe(long consumerGroupId, long topicId) {
        return getHandle().createQuery("CALL sp_topics_subscribe(:cg,:topic)")
                .bind("cg", consumerGroupId)
                .bind("topic", topicId)
                .mapTo(Long.class)
                .one();
    }

    default void subscribe(long consumerGroupId, List<Long> topicIds) {
        final var json = "[" + topicIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")) + "]";
        getHandle().createUpdate("CALL sp_topics_subscribe_multi(:cg,:topics)")
                .bind("cg", consumerGroupId)
                .bind("topics", json)
                .execute();
    }

    default void unsubscribe(long consumerGroupId, List<Long> topicIds) {
        final var json = "[" + topicIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")) + "]";
        getHandle().createUpdate("CALL sp_topics_unsubscribe_multi(:cg,:topics)")
                .bind("cg", consumerGroupId)
                .bind("topics", json)
                .execute();
    }

}
