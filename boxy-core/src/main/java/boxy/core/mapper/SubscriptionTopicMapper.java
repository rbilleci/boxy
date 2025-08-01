package boxy.core.mapper;

import boxy.core.domain.SubscriptionTopic;

import java.sql.ResultSet;
import java.sql.SQLException;

public class SubscriptionTopicMapper implements RowMapper<SubscriptionTopic> {
    @Override
    public SubscriptionTopic map(ResultSet rs) throws SQLException {
        return new SubscriptionTopic(
                rs.getLong("id"),
                rs.getLong("subscription_id"),
                rs.getLong("topic_id"));
    }
}
