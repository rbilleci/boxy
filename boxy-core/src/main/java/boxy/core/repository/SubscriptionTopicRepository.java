package boxy.core.repository;

import boxy.core.mapper.SubscriptionTopicMapper;
import boxy.core.domain.SubscriptionTopic;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;

public final class SubscriptionTopicRepository extends BaseRepository {

    private static final SubscriptionTopicMapper SUBSCRIPTION_TOPIC_MAPPER = new SubscriptionTopicMapper();

    public SubscriptionTopicRepository(DataSource ds) {
        super(ds);
    }

    public Optional<SubscriptionTopic> find(String tenant, String subscription, long namespaceId, String topic) {
        return queryOne("""
                        SELECT s.* FROM subscription_topics s
                            JOIN subscriptions sub ON s.subscription_id = sub.id
                            JOIN topics t ON s.topic_id = t.id
                            WHERE sub.tenant = ? AND
                                  sub.name = ? AND
                                  t.namespace_id = ? AND
                                  t.name = ?
                        """, SUBSCRIPTION_TOPIC_MAPPER,
                tenant, subscription, namespaceId, topic);
    }

    public long subscribe(String tenant, String subscription, long namespaceId, String topic) {
        return queryOne("{CALL sp_topics__subscribe(?,?,?,?)}", rs -> rs.getLong(1), tenant, subscription, namespaceId, topic)
                .orElseThrow();
    }

    public void unsubscribe(String tenant, String subscription, long namespaceId, String topic) {
        update("{CALL sp_topics__unsubscribe(?,?,?,?)}", tenant, subscription, namespaceId, topic);
    }

    public List<SubscriptionTopic> findAll(int limit, int offset) {
        return query("SELECT * FROM subscription_topics ORDER BY id LIMIT ? OFFSET ?", SUBSCRIPTION_TOPIC_MAPPER, limit, offset);
    }

}
