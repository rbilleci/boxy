package boxy.core.mapper;

import boxy.core.domain.Namespace;

import java.sql.ResultSet;
import java.sql.SQLException;

public class NamespaceMapper implements RowMapper<Namespace> {
    @Override
    public Namespace map(ResultSet rs) throws SQLException {
        return new Namespace(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getObject("parent_id", Long.class),
                rs.getString("path"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("last_modified_at").toInstant());
    }
}

