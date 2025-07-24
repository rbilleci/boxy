package boxy.core.dao;

import boxy.core.model.SubscriptionOffset;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;

public final class SubscriptionOffsetDao extends BaseDao {

    private static final RowMapper<SubscriptionOffset> MAPPER = rs -> new SubscriptionOffset(
            rs.getLong("id"),
            rs.getLong("subscription_id"),
            rs.getLong("partition_id"),
            rs.getLong("committed_offset"),
            rs.getLong("high_watermark"));

    public SubscriptionOffsetDao(DataSource ds) {
        super(ds);
    }

    public void commit(long id, long offset) {
        update("CALL sp_commit_offset(?,?)", id, offset);
    }

    public List<SubscriptionOffset> leasesAvailable(int limit, int offset) {
        return query("SELECT * FROM leases_available_view LIMIT ? OFFSET ?", MAPPER, limit, offset);
    }

    public Optional<SubscriptionOffset> find(long id) {
        return queryOne("SELECT * FROM subscription_offsets_view WHERE id = ?", MAPPER, id);
    }

    public Optional<SubscriptionOffset> find(long subscriptionId, long partitionId) {
        return queryOne("SELECT * FROM subscription_offsets_view WHERE subscription_id = ? AND partition_id = ?", MAPPER, subscriptionId, partitionId);
    }

    public List<SubscriptionOffset> findAll(long subscriptionId) {
        return query("SELECT * FROM subscription_offsets_view WHERE subscription_id = ? ORDER BY id", MAPPER, subscriptionId);
    }
}
