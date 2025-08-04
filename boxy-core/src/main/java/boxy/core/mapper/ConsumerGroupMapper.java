package boxy.core.mapper;

import boxy.core.domain.ConsumerGroup;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ConsumerGroupMapper implements RowMapper<ConsumerGroup> {
    @Override
    public ConsumerGroup map(final ResultSet rs) throws SQLException {
        return new ConsumerGroup(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getDouble("heartbeat_interval"),
                rs.getInt("active_partitions"),
                rs.getInt("active_consumers"),
                rs.getDouble("active_consumers_weight"),
                rs.getTimestamp("last_modified_at").toInstant());
    }
}
