package boxy.core.mapper;

import boxy.core.domain.Worker;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WorkerMapper implements RowMapper<Worker> {
    @Override
    public Worker map(ResultSet rs) throws SQLException {
        return new Worker(
                rs.getString("id"),
                rs.getLong("subscription_id"),
                rs.getDouble("weight"),
                rs.getTimestamp("heartbeat_detected_at").toInstant(),
                rs.getDouble("heartbeat_interval"),
                rs.getTimestamp("heartbeat_deadline").toInstant());
    }
}
