package boxy.core.mapper;

import boxy.core.model.Worker;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WorkerMapper implements RowMapper<Worker> {
    @Override
    public Worker map(ResultSet rs) throws SQLException {
        return new Worker(
                rs.getLong("id"),
                rs.getString("node_id"),
                rs.getLong("consumer_group_id"),
                rs.getDouble("weight"),
                rs.getTimestamp("last_heartbeat").toInstant(),
                rs.getDouble("heartbeat_interval"),
                rs.getTimestamp("heartbeat_deadline").toInstant());
    }
}
