package boxy.core.mapper;

import boxy.core.domain.Topic;

import java.sql.ResultSet;
import java.sql.SQLException;

public class TopicMapper implements RowMapper<Topic> {
    @Override
    public Topic map(final ResultSet rs) throws SQLException {
        return new Topic(
                rs.getLong("id"),
                rs.getLong("namespace_id"),
                rs.getString("name"),
                rs.getInt("partitions"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("last_modified_at").toInstant());
    }
}
