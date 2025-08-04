package boxy.core.mapper;

import boxy.core.domain.Consumer;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ConsumerMapper implements RowMapper<Consumer> {
    @Override
    public Consumer map(ResultSet rs) throws SQLException {
        return new Consumer(
                rs.getString("id"),
                rs.getLong("consumer_group_id"),
                rs.getDouble("weight"),
                rs.getTimestamp("heartbeat_detected_at").toInstant(),
                rs.getDouble("heartbeat_interval"),
                rs.getTimestamp("heartbeat_deadline").toInstant());
    }
}
