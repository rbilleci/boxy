package boxy.core.mapper;

import boxy.core.model.SubscriptionOffset;

import java.sql.ResultSet;
import java.sql.SQLException;

public class SubscriptionOffsetMapper implements RowMapper<SubscriptionOffset> {
    @Override
    public SubscriptionOffset map(ResultSet rs) throws SQLException {
        return new SubscriptionOffset(
                rs.getLong("id"),
                rs.getLong("subscription_id"),
                rs.getLong("partition_id"),
                rs.getLong("committed_offset"));
    }
}
