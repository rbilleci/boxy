package boxy.core.dao;

import boxy.core.model.Lease;
import boxy.core.model.SubscriptionOffset;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public final class LeaseDao extends BaseDao {

    private static final RowMapper<Lease> MAPPER = rs -> new Lease(
            rs.getLong("subscription_offset_id"),
            rs.getLong("worker_id"),
            rs.getLong("version"),
            rs.getTimestamp("acquired_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant(),
            rs.getTimestamp("expires_at").toInstant());

    public LeaseDao(DataSource ds) {
        super(ds);
    }

    public Optional<Lease> find(long subscriptionOffsetId) {
        return queryOne("SELECT * FROM leases WHERE subscription_offset_id = ?", MAPPER, subscriptionOffsetId);
    }

    public boolean acquire(long subscriptionOffsetId, long workerId, long expiresAfter) {
        return queryOne("CALL sp_leases_acquire(?,?,?)", rs -> rs.getInt(1), subscriptionOffsetId, workerId, expiresAfter)
                       .orElse(0) > 0;
    }

    public boolean renew(long subscriptionOffsetId, long workerId, long expiresAfter) {
        return queryOne("CALL sp_leases_renew(?,?,?)", rs -> rs.getInt(1), subscriptionOffsetId, workerId, expiresAfter)
                       .orElse(0) > 0;
    }

    public void release(long subscriptionOffsetId, long workerId) {
        update("CALL sp_leases_release(?,?)", subscriptionOffsetId, workerId);
    }
    
    /**
     * Counts the number of leases held by a worker.
     *
     * @param workerId The worker ID
     * @return The number of leases held by the worker
     */
    public int countByWorkerId(long workerId) {
        return queryOne("SELECT COUNT(*) FROM leases WHERE worker_id = ?", rs -> rs.getInt(1), workerId)
                .orElse(0);
    }
    
    /**
     * Retrieves all leases held by a worker.
     *
     * @param workerId The worker ID
     * @return List of leases held by the worker
     */
    public List<Lease> findByWorkerId(long workerId) {
        return query("SELECT * FROM leases WHERE worker_id = ?", MAPPER, workerId);
    }
    
    /**
     * Attempts to acquire multiple leases for a worker.
     * This is used in the work-stealing algorithm to grab leases.
     *
     * @param subscriptionOffsets The subscription offsets to acquire leases for
     * @param workerId The worker ID
     * @param expiresAfter The number of milliseconds after which the leases expire
     * @return The number of leases successfully acquired
     */
    public int acquireBatch(List<SubscriptionOffset> subscriptionOffsets, long workerId, long expiresAfter) {
        int acquired = 0;
        for (SubscriptionOffset offset : subscriptionOffsets) {
            if (acquire(offset.id(), workerId, expiresAfter)) {
                acquired++;
            }
        }
        return acquired;
    }
    
    /**
     * Releases multiple leases held by a worker.
     * This is used in the work-stealing algorithm to release leases.
     *
     * @param leases The leases to release
     * @param workerId The worker ID
     * @return The number of leases successfully released
     */
    public int releaseBatch(List<Lease> leases, long workerId) {
        int released = 0;
        for (Lease lease : leases) {
            if (lease.workerId() == workerId) {
                release(lease.subscriptionOffsetId(), workerId);
                released++;
            }
        }
        return released;
    }
    
    /**
     * Releases a specific number of leases held by a worker.
     * This is used in the work-stealing algorithm to release excess leases.
     *
     * @param workerId The worker ID
     * @param count The number of leases to release
     * @return The number of leases successfully released
     */
    public int releaseCount(long workerId, int count) {
        if (count <= 0) {
            return 0;
        }
        
        List<Lease> leases = findByWorkerId(workerId);
        if (leases.size() <= count) {
            return releaseBatch(leases, workerId);
        } else {
            // Release only the specified number of leases
            return releaseBatch(leases.subList(0, count), workerId);
        }
    }
}
