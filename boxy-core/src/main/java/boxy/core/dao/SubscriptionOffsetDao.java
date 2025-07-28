package boxy.core.dao;

import boxy.core.mapper.SubscriptionOffsetMapper;
import boxy.core.model.SubscriptionOffset;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;

public final class SubscriptionOffsetDao extends BaseDao {

    private static final SubscriptionOffsetMapper SUBSCRIPTION_OFFSET_MAPPER = new SubscriptionOffsetMapper();

    public SubscriptionOffsetDao(DataSource ds) {
        super(ds);
    }

    public void commit(long id, long offset) {
        update("CALL sp_commit_offset(?,?)", id, offset);
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
}
