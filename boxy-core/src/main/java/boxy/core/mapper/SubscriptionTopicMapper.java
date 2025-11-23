package boxy.core.mapper;

import boxy.core.domain.SubscriptionTopic;

import java.sql.ResultSet;
import java.sql.SQLException;

public class SubscriptionTopicMapper implements RowMapper<SubscriptionTopic> {
    @Override
    public SubscriptionTopic map(final ResultSet rs) throws SQLException {
        return new SubscriptionTopic(
                rs.getLong("id"),
                rs.getLong("subscription_id"),
                rs.getLong("topic_id"),
                rs.getDouble("heartbeat_interval"),
                rs.getInt("active_partitions"),
                rs.getInt("active_consumers"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("last_modified_at").toInstant());
    }
}
