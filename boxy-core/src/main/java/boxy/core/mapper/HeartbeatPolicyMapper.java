package boxy.core.mapper;

import boxy.core.domain.HeartbeatPolicy;

import java.sql.ResultSet;
import java.sql.SQLException;

public class HeartbeatPolicyMapper implements RowMapper<HeartbeatPolicy> {
    @Override
    public HeartbeatPolicy map(final ResultSet rs) throws SQLException {
        return new HeartbeatPolicy(
                rs.getDouble("heartbeat_deadline_multiplier"),
                rs.getDouble("heartbeat_interval_baseline"),
                rs.getDouble("heartbeat_interval_limit"),
                rs.getDouble("heartbeat_target_qps"));
    }
}
