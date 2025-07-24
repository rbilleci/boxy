package boxy.core.dao;

import boxy.core.model.Subscription;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class SubscriptionDao extends BaseDao {

    private static final RowMapper<Subscription> MAPPER = rs -> new Subscription(
            rs.getLong("id"),
            rs.getLong("consumer_group_id"),
            rs.getLong("topic_id"));

    public SubscriptionDao(DataSource ds) {
        super(ds);
    }

    public Optional<Subscription> find(long id) {
        return queryOne("SELECT * FROM subscriptions WHERE id = ?", MAPPER, id);
    }

    public Optional<Subscription> find(long consumerGroupId, long topicId) {
        return queryOne("SELECT * FROM subscriptions WHERE consumer_group_id = ? AND topic_id = ?", MAPPER, consumerGroupId, topicId);
    }

    public List<Subscription> findAll(int limit, int offset) {
        return query("SELECT * FROM subscriptions ORDER BY id LIMIT ? OFFSET ?", MAPPER, limit, offset);
    }

    public long subscribe(long consumerGroupId, long topicId) {
        return queryOne("CALL sp_topics_subscribe(?,?)", rs -> rs.getLong(1), consumerGroupId, topicId).orElseThrow();
    }

    public void subscribe(long consumerGroupId, List<Long> topicIds) {
        final var json = "[" + topicIds.stream().map(String::valueOf).collect(Collectors.joining(",")) + "]";
        update("CALL sp_topics_subscribe_multi(?,?)", consumerGroupId, json);
    }

    public void unsubscribe(long consumerGroupId, List<Long> topicIds) {
        final var json = "[" + topicIds.stream().map(String::valueOf).collect(Collectors.joining(",")) + "]";
        update("CALL sp_topics_unsubscribe_multi(?,?)", consumerGroupId, json);
    }
}
