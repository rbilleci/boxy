package boxy.core.mapper;

import boxy.core.domain.Lease;

import java.sql.ResultSet;
import java.sql.SQLException;

public class LeaseMapper implements RowMapper<Lease> {
    @Override
    public Lease map(ResultSet rs) throws SQLException {
        return new Lease(
                rs.getLong("cursor_id"),
                rs.getString("worker_id"),
                rs.getLong("version"),
                rs.getTimestamp("acquired_at").toInstant(),
                rs.getTimestamp("released_at") != null ? rs.getTimestamp("released_at").toInstant() : null,
                rs.getTimestamp("release_deadline") != null ? rs.getTimestamp("release_deadline").toInstant() : null,
                Lease.LeaseState.valueOf(rs.getString("state")));
    }
}
