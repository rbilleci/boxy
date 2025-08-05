package boxy.core.mapper;

import boxy.core.domain.Cursor;

import java.sql.ResultSet;
import java.sql.SQLException;

public class CursorMapper implements RowMapper<Cursor> {
    @Override
    public Cursor map(final ResultSet rs) throws SQLException {
        return new Cursor(
                rs.getLong("id"),
                rs.getLong("subscription_topic_id"),
                rs.getLong("subscription_id"),
                rs.getLong("partition_id"),
                rs.getInt("random_key"),
                rs.getLong("position"));
    }
}
