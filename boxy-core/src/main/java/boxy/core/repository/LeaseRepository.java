package boxy.core.repository;

import boxy.core.domain.Lease;
import boxy.core.mapper.LeaseMapper;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;

public final class LeaseRepository extends BaseRepository {

    private static final LeaseMapper LEASE_MAPPER = new LeaseMapper();

    public LeaseRepository(final DataSource ds) {
        super(ds);
    }

    public Optional<Lease> find(final long cursorId) {
        return queryOne("SELECT * FROM leases WHERE cursor_id = ?", LEASE_MAPPER, cursorId);
    }

    public List<Lease> findByConsumer(final String consumerId) {
        return query("SELECT * FROM leases WHERE consumer_id = ? ORDER BY cursor_id", LEASE_MAPPER, consumerId);
    }
}
