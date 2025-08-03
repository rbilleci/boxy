package boxy.core.mapper;

import boxy.core.domain.Subscription;

import java.sql.ResultSet;
import java.sql.SQLException;

public class SubscriptionMapper implements RowMapper<Subscription> {
    @Override
    public Subscription map(ResultSet rs) throws SQLException {
        return new Subscription(
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
                rs.getInt("active_workers"),
                rs.getInt("active_workers_limit"),
                rs.getDouble("active_workers_weight"),
                rs.getTimestamp("last_modified_at").toInstant());
    }
}
