package boxy.core.repository;

import boxy.core.mapper.LeaseMapper;
import boxy.core.domain.Lease;

import javax.sql.DataSource;
import java.util.Optional;

public final class LeaseRepository extends BaseRepository {


    private static final LeaseMapper LEASE_MAPPER = new LeaseMapper();

    public LeaseRepository(DataSource ds) {
        super(ds);
    }

    public Optional<Lease> find(long cursorId) {
        return queryOne("SELECT * FROM leases WHERE cursor_id = ?", LEASE_MAPPER, cursorId);
    }

    public void release(long cursorId, String workerId) {
        update("{CALL sp_leases__release(?,?)}", cursorId, workerId);
    }

}
