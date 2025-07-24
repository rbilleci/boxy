package boxy.core.dao;

import boxy.core.jdbc.BaseDao;
import boxy.core.jdbc.RowMapper;
import boxy.core.model.Lease;
import boxy.core.model.SubscriptionOffset;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class LeaseDao extends BaseDao {
    private static final RowMapper<Lease> MAPPER = rs -> new Lease(
            rs.getLong("subscription_offset_id"),
            rs.getLong("worker_id"),
            rs.getLong("version"),
            rs.getTimestamp("acquired_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant(),
            rs.getTimestamp("expires_at").toInstant()
    );
    private static final RowMapper<SubscriptionOffset> OFFSET_MAPPER = rs -> new SubscriptionOffset(
            rs.getLong("id"),
            rs.getLong("subscription_id"),
            rs.getLong("partition_id"),
            rs.getLong("committed_offset"),
            rs.getLong("high_watermark")
    );

    public LeaseDao(DataSource ds) {
        super(ds);
    }

    public Optional<Lease> find(long subscriptionOffsetId) throws SQLException {
        return queryOne("SELECT * FROM leases WHERE subscription_offset_id = ?", MAPPER, subscriptionOffsetId);
    }

    public List<SubscriptionOffset> leasesAvailable(int limit, int offset) throws SQLException {
        return query("SELECT * FROM leases_available_view LIMIT ? OFFSET ?", OFFSET_MAPPER, limit, offset);
    }

    public boolean acquire(long subscriptionOffsetId, long workerId, long expiresAfter) throws SQLException {
        return queryOne("CALL sp_leases_acquire(?,?,?)", rs -> rs.getInt(1), subscriptionOffsetId, workerId, expiresAfter)
                .orElse(0) > 0;
    }

    public boolean renew(long subscriptionOffsetId, long workerId, long expiresAfter) throws SQLException {
        return queryOne("CALL sp_leases_renew(?,?,?)", rs -> rs.getInt(1), subscriptionOffsetId, workerId, expiresAfter)
                .orElse(0) > 0;
    }

    public void release(long subscriptionOffsetId, long workerId) throws SQLException {
        update("CALL sp_leases_release(?,?)", subscriptionOffsetId, workerId);
    }
}
