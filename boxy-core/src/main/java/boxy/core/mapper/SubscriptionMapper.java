package boxy.core.mapper;

import boxy.core.domain.Subscription;

import java.sql.ResultSet;
import java.sql.SQLException;

public class SubscriptionMapper implements RowMapper<Subscription> {
    @Override
    public Subscription map(ResultSet rs) throws SQLException {
        return new Subscription(
                rs.getLong("id"),
                rs.getLong("consumer_group_id"),
                rs.getLong("topic_id"),
                rs.getTimestamp("created_at").toInstant());
    }
}
