package boxy.core.mapper;

import boxy.core.model.Topic;

import java.sql.ResultSet;
import java.sql.SQLException;

public class TopicMapper implements RowMapper<Topic> {
    @Override
    public Topic map(ResultSet rs) throws SQLException {
        return new Topic(
                rs.getLong("id"),
                rs.getString("tenant"),
                rs.getString("name"),
                rs.getInt("partitions"));
    }
}
