package boxy.core.mapper;

import boxy.core.domain.Subscription;

import java.sql.ResultSet;
import java.sql.SQLException;

public class SubscriptionMapper implements RowMapper<Subscription> {
    @Override
    public Subscription map(final ResultSet rs) throws SQLException {
        return new Subscription(
                rs.getLong("id"),
                rs.getLong("consumer_group_id"),
                rs.getLong("topic_id"),
                rs.getDouble("heartbeat_interval"),
                rs.getInt("active_partitions"),
                rs.getInt("active_consumers"),
                rs.getDouble("active_consumers_weight"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("last_modified_at").toInstant());
    }
}
