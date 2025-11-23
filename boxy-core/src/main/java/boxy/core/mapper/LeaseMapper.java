package boxy.core.mapper;

import boxy.core.domain.Lease;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

public class LeaseMapper implements RowMapper<Lease> {
    @Override
    public Lease map(final ResultSet rs) throws SQLException {
        final Timestamp lockedUntil = rs.getTimestamp("locked_until");
        final Instant lockedUntilInstant = lockedUntil == null ? null : lockedUntil.toInstant();
        return new Lease(
                rs.getLong("cursor_id"),
                rs.getString("session_id"),
                lockedUntilInstant);
    }
}
