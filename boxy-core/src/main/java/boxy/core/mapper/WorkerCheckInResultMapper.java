package boxy.core.mapper;

import boxy.core.model.WorkerCheckInResult;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

public class WorkerCheckInResultMapper implements RowMapper<WorkerCheckInResult> {
    @Override
    public WorkerCheckInResult map(ResultSet rs) throws SQLException {
        return new WorkerCheckInResult(
                rs.getLong("worker_id"),
                rs.getInt("active_workers"),
                rs.getInt("active_partitions"),
                rs.getInt("total_weight"),
                rs.getInt("worker_weight"),
                rs.getDouble("ideal_share"),
                rs.getInt("current_leases"),
                rs.getInt("min_leases"),
                rs.getInt("max_leases"),
                rs.getInt("heartbeat_interval"),
                rs.getInt("lease_ttl"),
                new ArrayList<>(),
                new ArrayList<>());
    }
}
