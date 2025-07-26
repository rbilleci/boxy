package boxy.core.dao;

import boxy.core.mapper.LeaseMapper;
import boxy.core.model.Lease;

import javax.sql.DataSource;
import java.util.Optional;

public final class LeaseDao extends BaseDao {


    private static final LeaseMapper LEASE_MAPPER = new LeaseMapper();

    public LeaseDao(DataSource ds) {
        super(ds);
    }

    public Optional<Lease> find(long subscriptionOffsetId) {
        return queryOne("SELECT * FROM leases WHERE subscription_offset_id = ?",
                LEASE_MAPPER,
                subscriptionOffsetId);
    }

    public boolean acquire(long subscriptionOffsetId, long workerId) {
        return queryOne("CALL sp_leases_acquire(?,?)", rs -> rs.getInt(1), subscriptionOffsetId, workerId)
                       .orElse(0) > 0;
    }

    public void release(long subscriptionOffsetId, long workerId) {
        update("CALL sp_leases_release(?,?)", subscriptionOffsetId, workerId);
    }

}
