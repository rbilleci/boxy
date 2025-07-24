package boxy.core.dao;

import boxy.core.model.Lease;

import javax.sql.DataSource;
import java.util.Optional;

public class LeaseDao extends BaseDao {

    private static final RowMapper<Lease> MAPPER = rs -> new Lease(
            rs.getLong("subscription_offset_id"),
            rs.getLong("worker_id"),
            rs.getLong("version"),
            rs.getTimestamp("acquired_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant(),
            rs.getTimestamp("expires_at").toInstant());

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

    public void release(long subscriptionOffsetId, long workerId) {
        update("CALL sp_leases_release(?,?)", subscriptionOffsetId, workerId);
    }
}
