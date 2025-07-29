package boxy.core.repository;

import boxy.core.mapper.LeaseMapper;
import boxy.core.model.Lease;

import javax.sql.DataSource;
import java.util.Optional;

public final class LeaseRepository extends BaseRepository {


    private static final LeaseMapper LEASE_MAPPER = new LeaseMapper();

    public LeaseRepository(DataSource ds) {
        super(ds);
    }

    public Optional<Lease> find(long subscriptionOffsetId) {
        return queryOne("SELECT * FROM leases WHERE subscription_offset_id = ?", LEASE_MAPPER, subscriptionOffsetId);
    }

    public void release(long subscriptionOffsetId, String workerId) {
        update("{CALL sp_leases_release(?,?)}", subscriptionOffsetId, workerId);
    }

}
