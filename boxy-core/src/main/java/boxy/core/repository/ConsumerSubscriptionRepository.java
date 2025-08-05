package boxy.core.repository;

import boxy.core.domain.ConsumerSubscription;
import boxy.core.mapper.ConsumerSubscriptionMapper;

import javax.sql.DataSource;
import java.util.List;

public final class ConsumerSubscriptionRepository extends BaseRepository {

    private static final ConsumerSubscriptionMapper CONSUMER_SUBSCRIPTION_MAPPER = new ConsumerSubscriptionMapper();

    public ConsumerSubscriptionRepository(final DataSource ds) {
        super(ds);
    }

    public void subscribe(final String consumerId, final String path, final String topic) {
        update("{CALL sp_consumers__subscribe(?,?,?)}", consumerId, path, topic);
    }

    public void unsubscribe(final String consumerId, final String path, final String topic) {
        update("{CALL sp_consumers__unsubscribe(?,?,?)}", consumerId, path, topic);
    }

    public List<ConsumerSubscription> findAll(final String consumerId) {
        return query("SELECT * FROM consumer_subscriptions WHERE consumer_id = ? ORDER BY topic_id",
                CONSUMER_SUBSCRIPTION_MAPPER, consumerId);
    }
}
