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
                rs.getDouble("heartbeat_interval_min"),
                rs.getDouble("heartbeat_interval_max"),
                rs.getDouble("heartbeat_deadline_multiplier"),
                rs.getDouble("heartbeat_qps_target"),
                rs.getInt("release_deadline"),
                rs.getInt("active_partitions"),
                rs.getInt("active_workers"),
                rs.getInt("active_workers_weight"),
                rs.getTimestamp("last_updated").toInstant());
    }
}
