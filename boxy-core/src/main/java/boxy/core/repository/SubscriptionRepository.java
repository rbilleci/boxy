package boxy.core.repository;

import boxy.core.mapper.SubscriptionMapper;
import boxy.core.domain.Subscription;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;

public final class SubscriptionRepository extends BaseRepository {

    private static final SubscriptionMapper SUBSCRIPTION_MAPPER = new SubscriptionMapper();

    public SubscriptionRepository(DataSource ds) {
        super(ds);
    }

    public Optional<Subscription> find(final String consumerGroup, final String path, final String topic) {
        return queryOne("""
                SELECT st.* FROM subscriptions st
                    JOIN consumer_groups s ON st.consumer_group_id = s.id
                    JOIN topics t ON st.topic_id = t.id
                    JOIN namespaces n ON n.id = t.namespace_id
                    WHERE s.name = ?
                      AND n.path_hash = UNHEX(MD5(?))
                      AND n.path      = ?
                      AND t.name      = ?
                """, SUBSCRIPTION_MAPPER, consumerGroup, path, path, topic);
    }


    public List<Subscription> findAll(final int limit, final int offset) {
        return query("SELECT * FROM subscriptions ORDER BY id LIMIT ? OFFSET ?", SUBSCRIPTION_MAPPER, limit, offset);
    }

}
