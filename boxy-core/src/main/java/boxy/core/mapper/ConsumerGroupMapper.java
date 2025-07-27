package boxy.core.mapper;

import boxy.core.model.ConsumerGroup;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ConsumerGroupMapper implements RowMapper<ConsumerGroup> {
    @Override
    public ConsumerGroup map(ResultSet rs) throws SQLException {
        return new ConsumerGroup(
                rs.getLong("id"),
                rs.getString("tenant"),
                rs.getString("name"),
                rs.getDouble("heartbeat_interval_default"),
                rs.getDouble("heartbeat_deadline_multiplier"),
                rs.getInt("active_workers_count"),
                rs.getInt("total_weight"),
                rs.getInt("active_partitions_count"),
                rs.getTimestamp("last_updated").toInstant());
    }
}
