package boxy.core.mapper;

import boxy.core.domain.ConsumerSubscription;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ConsumerSubscriptionMapper implements RowMapper<ConsumerSubscription> {
    @Override
    public ConsumerSubscription map(final ResultSet rs) throws SQLException {
        return new ConsumerSubscription(
                rs.getString("consumer_id"),
                rs.getLong("subscription_topic_id"),
                rs.getLong("topic_id"));
    }
}
