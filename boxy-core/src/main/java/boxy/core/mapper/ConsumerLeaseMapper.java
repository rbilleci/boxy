package boxy.core.mapper;

import boxy.core.domain.ConsumerLease;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

public class ConsumerLeaseMapper implements RowMapper<ConsumerLease> {
    @Override
    public ConsumerLease map(final ResultSet rs) throws SQLException {
        final Timestamp lockedUntil = rs.getTimestamp("locked_until");
        final Instant lockedUntilInstant = lockedUntil == null ? null : lockedUntil.toInstant();
        return new ConsumerLease(
                rs.getLong("id"),
                rs.getString("consumer_id"),
                rs.getLong("cursor_id"),
                lockedUntilInstant,
                rs.getLong("last_read_position"));
    }
}
