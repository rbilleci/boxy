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

    public Optional<SubscriptionTopic> find(final String subscription, final String path, final String topic) {
        return queryOne("""
                SELECT st.* FROM subscription_topics st
                    JOIN subscriptions s ON st.subscription_id = s.id
                    JOIN topics t ON st.topic_id = t.id
                    JOIN namespaces n ON n.id = t.namespace_id
                    WHERE s.name = ?
                      AND n.path_hash = UNHEX(MD5(?))
                      AND n.path      = ?
                      AND t.name      = ?
                """, SUBSCRIPTION_TOPIC_MAPPER, subscription, path, path, topic);
    }


    public List<SubscriptionTopic> findAll(final int limit, final int offset) {
        return query("SELECT * FROM subscription_topics ORDER BY id LIMIT ? OFFSET ?", SUBSCRIPTION_TOPIC_MAPPER, limit, offset);
    }

}
