package boxy.core.dao;

import boxy.core.model.Lease;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.util.Optional;

public final class LeaseDao extends BaseDao {

    private static final RowMapper<Lease> MAPPER = rs -> new Lease(
            rs.getLong("subscription_offset_id"),
            rs.getLong("worker_id"),
            rs.getLong("version"),
            rs.getTimestamp("acquired_at").toInstant(),
            rs.getString("status"),
            getTimestampOrNull(rs, "release_started_at"));

    public LeaseDao(DataSource ds) {
        super(ds);
    }

    public Optional<Lease> find(long subscriptionOffsetId) {
        return queryOne("SELECT * FROM leases WHERE subscription_offset_id = ?", MAPPER, subscriptionOffsetId);
    }

    public boolean acquire(long subscriptionOffsetId, long workerId, long expiresAfter) {
        return queryOne("CALL sp_leases_acquire(?,?,?)", rs -> rs.getInt(1), subscriptionOffsetId, workerId, expiresAfter)
                       .orElse(0) > 0;
    }

    public boolean renew(long subscriptionOffsetId, long workerId, long expiresAfter) {
        return queryOne("CALL sp_leases_renew(?,?,?)", rs -> rs.getInt(1), subscriptionOffsetId, workerId, expiresAfter)
                       .orElse(0) > 0;
    }

    public boolean release(long subscriptionOffsetId, long workerId) {
        return queryOne("CALL sp_leases_release(?,?)", rs -> rs.getInt(1), subscriptionOffsetId, workerId)
                       .orElse(0) > 0;
    }
    
    private static java.time.Instant getTimestampOrNull(java.sql.ResultSet rs, String columnName) throws java.sql.SQLException {
        Timestamp timestamp = rs.getTimestamp(columnName);
        return timestamp != null ? timestamp.toInstant() : null;
    }
}
