package boxy.core.dao;

import boxy.core.jdbc.BaseDao;
import boxy.core.jdbc.RowMapper;
import boxy.core.model.SubscriptionOffset;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class SubscriptionOffsetDao extends BaseDao {
    private static final RowMapper<SubscriptionOffset> MAPPER = rs -> new SubscriptionOffset(
            rs.getLong("id"),
            rs.getLong("subscription_id"),
            rs.getLong("partition_id"),
            rs.getLong("committed_offset"),
            rs.getLong("high_watermark")
    );

    public SubscriptionOffsetDao(DataSource ds) {
        super(ds);
    }

    public void commit(long id, long offset) throws SQLException {
        update("CALL sp_commit_offset(?,?)", id, offset);
    }

    public Optional<SubscriptionOffset> find(long id) throws SQLException {
        return queryOne("SELECT * FROM subscription_offsets_view WHERE id = ?", MAPPER, id);
    }

    public Optional<SubscriptionOffset> find(long subscriptionId, long partitionId) throws SQLException {
        return queryOne("SELECT * FROM subscription_offsets_view WHERE subscription_id = ? AND partition_id = ?", MAPPER, subscriptionId, partitionId);
    }

    public List<SubscriptionOffset> findAll(long subscriptionId) throws SQLException {
        return query("SELECT * FROM subscription_offsets_view WHERE subscription_id = ? ORDER BY id", MAPPER, subscriptionId);
    }
}
