package boxy.core.repository;

import boxy.core.mapper.SubscriptionOffsetMapper;
import boxy.core.domain.SubscriptionOffset;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;

public final class SubscriptionOffsetRepository extends BaseRepository {

    private static final SubscriptionOffsetMapper SUBSCRIPTION_OFFSET_MAPPER = new SubscriptionOffsetMapper();

    public SubscriptionOffsetRepository(DataSource ds) {
        super(ds);
    }

    public void commit(long id, long offset) {
        update("{CALL sp_commit_offset(?,?)}", id, offset);
    }

    public Optional<SubscriptionOffset> find(long id) {
        return queryOne("SELECT * FROM subscription_offsets WHERE id = ?", SUBSCRIPTION_OFFSET_MAPPER, id);
    }

    public Optional<SubscriptionOffset> find(long subscriptionId, long partitionId) {
        return queryOne("SELECT * FROM subscription_offsets WHERE subscription_id = ? AND partition_id = ?", SUBSCRIPTION_OFFSET_MAPPER, subscriptionId, partitionId);
    }

    public List<SubscriptionOffset> findAll(long subscriptionId) {
        return query("SELECT * FROM subscription_offsets WHERE subscription_id = ? ORDER BY id", SUBSCRIPTION_OFFSET_MAPPER, subscriptionId);
    }


    public List<SubscriptionOffset> findLeasable(int limit, int offset) {
        return query(
                "SELECT * FROM unleased_subscription_offsets_view ORDER BY id LIMIT ? OFFSET ?",
                SUBSCRIPTION_OFFSET_MAPPER,
                limit, offset);
    }

    public List<SubscriptionOffset> findLeasable(long subscriptionId, int limit, int offset) {
        return query(
                "SELECT * FROM unleased_subscription_offsets_view WHERE subscription_id = ? ORDER BY id LIMIT ? OFFSET ?",
                SUBSCRIPTION_OFFSET_MAPPER,
                subscriptionId, limit, offset);
    }

}
