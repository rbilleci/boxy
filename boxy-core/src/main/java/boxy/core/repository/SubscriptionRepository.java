package boxy.core.repository;

import boxy.core.mapper.SubscriptionMapper;
import boxy.core.model.Subscription;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;

public final class SubscriptionRepository extends BaseRepository {

    private static final SubscriptionMapper SUBSCRIPTION_MAPPER = new SubscriptionMapper();

    public SubscriptionRepository(DataSource ds) {
        super(ds);
    }

    public Optional<Subscription> find(String tenant, String consumerGroup, String topic) {
        return queryOne("""
                        SELECT * FROM subscriptions s
                            JOIN consumer_groups cg ON s.consumer_group_id = cg.id
                            JOIN topics t ON s.topic_id = t.id
                            WHERE cg.tenant = ? AND
                                cg.name = ? AND
                                t.name = ?
                        """, SUBSCRIPTION_MAPPER,
                tenant, consumerGroup, topic);
    }

    public long subscribe(String tenant, String consumerGroup, String topic) {
        return queryOne("CALL sp_topics_subscribe(?,?,?)", rs -> rs.getLong(1), tenant, consumerGroup, topic)
                .orElseThrow();
    }

    public void unsubscribe(String tenant, String consumerGroup, String topic) {
        update("CALL sp_topics_unsubscribe(?,?,?)", tenant, consumerGroup, topic);
    }

    public List<Subscription> findAll(int limit, int offset) {
        return query("SELECT * FROM subscriptions ORDER BY id LIMIT ? OFFSET ?", SUBSCRIPTION_MAPPER, limit, offset);
    }

}
