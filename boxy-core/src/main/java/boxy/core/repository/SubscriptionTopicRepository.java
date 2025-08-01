package boxy.core.repository;

import boxy.core.mapper.IdMapper;
import boxy.core.mapper.RowMapper;
import boxy.core.mapper.SubscriptionTopicMapper;
import boxy.core.domain.SubscriptionTopic;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;

public final class SubscriptionTopicRepository extends BaseRepository {

    private static final SubscriptionTopicMapper SUBSCRIPTION_TOPIC_MAPPER = new SubscriptionTopicMapper();
    private static final RowMapper<Long> ID_MAPPER = new IdMapper();

    public SubscriptionTopicRepository(DataSource ds) {
        super(ds);
    }

    public Optional<SubscriptionTopic> find(String tenant, String subscription, String namespace, String topic) {
        return queryOne("""
                        SELECT st.* FROM subscription_topics st
                            JOIN subscriptions s ON st.subscription_id = s.id
                            JOIN topics t ON st.topic_id = t.id
                            JOIN namespaces n ON t.namespace_id = n.id
                            WHERE s.tenant = ? AND
                                  s.name = ? AND
                                  n.name = ? AND
                                  t.name = ?
                        """, SUBSCRIPTION_TOPIC_MAPPER,
                tenant, subscription, namespace, topic);
    }

    public long subscribe(String tenant, String subscription, String namespace, String topic) {
        return queryOne("{CALL sp_topics__subscribe(?,?,?,?)}", ID_MAPPER, tenant, subscription, namespace, topic)
                .orElseThrow();
    }

    public void unsubscribe(String tenant, String subscription, String namespace, String topic) {
        update("{CALL sp_topics__unsubscribe(?,?,?,?)}", tenant, subscription, namespace, topic);
    }

    public List<SubscriptionTopic> findAll(int limit, int offset) {
        return query("SELECT * FROM subscription_topics ORDER BY id LIMIT ? OFFSET ?", SUBSCRIPTION_TOPIC_MAPPER, limit, offset);
    }

}
