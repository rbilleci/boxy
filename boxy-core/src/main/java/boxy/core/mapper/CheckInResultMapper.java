package boxy.core.mapper;

import boxy.core.domain.CheckInResult;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

public class CheckInResultMapper implements RowMapper<CheckInResult> {
    @Override
    public CheckInResult map(ResultSet rs) throws SQLException {
        return new CheckInResult(
                rs.getString("worker_id"),
                rs.getInt("active_workers"),
                rs.getDouble("active_workers_weight"),
                rs.getInt("active_partitions"),
                rs.getDouble("worker_weight"),
                rs.getDouble("ideal_share"),
                rs.getInt("current_leases"),
                rs.getInt("min_leases"),
                rs.getInt("max_leases"),
                rs.getDouble("heartbeat_interval"),
                rs.getTimestamp("heartbeat_deadline").toInstant(),
                new ArrayList<>());
    }
}
