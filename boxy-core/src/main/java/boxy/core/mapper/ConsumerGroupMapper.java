package boxy.core.mapper;

import boxy.core.domain.ConsumerGroup;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ConsumerGroupMapper implements RowMapper<ConsumerGroup> {
    @Override
    public ConsumerGroup map(final ResultSet rs) throws SQLException {
        return new ConsumerGroup(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getTimestamp("last_modified_at").toInstant());
    }
}
