package boxy.core.mapper;

import boxy.core.domain.ConsumerGroup;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ConsumerGroupMapper implements RowMapper<ConsumerGroup> {
    @Override
    public ConsumerGroup map(ResultSet rs) throws SQLException {
        return new ConsumerGroup(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getDouble("heartbeat_deadline_multiplier"),
                rs.getDouble("heartbeat_interval_baseline"),
                rs.getDouble("heartbeat_interval"),
                rs.getDouble("heartbeat_interval_limit"),
                rs.getDouble("heartbeat_target_qps"),
                rs.getInt("metrics_refresh_interval"),
                rs.getInt("lease_release_period"),
                rs.getInt("active_partitions"),
                rs.getInt("active_consumers"),
                rs.getInt("active_consumers_limit"),
                rs.getDouble("active_consumers_weight"),
                rs.getTimestamp("last_modified_at").toInstant());
    }
}
