package boxy.core.repository;

import boxy.core.domain.ConsumerLease;
import boxy.core.mapper.ConsumerLeaseMapper;
import boxy.core.repository.BaseRepository;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;

public final class ConsumerLeaseRepository extends BaseRepository {

    private static final ConsumerLeaseMapper LEASE_MAPPER = new ConsumerLeaseMapper();

    public ConsumerLeaseRepository(final DataSource ds) {
        super(ds);
    }

    public Optional<ConsumerLease> find(final long id) {
        return queryOne("SELECT * FROM consumer_leases WHERE id = ?", LEASE_MAPPER, id);
    }

    public Optional<ConsumerLease> findByCursor(final long cursorId) {
        return queryOne("SELECT * FROM consumer_leases WHERE cursor_id = ?", LEASE_MAPPER, cursorId);
    }

    public List<ConsumerLease> findByConsumer(final String consumerId) {
        return query("SELECT * FROM consumer_leases WHERE consumer_id = ? ORDER BY cursor_id", LEASE_MAPPER, consumerId);
    }
}
