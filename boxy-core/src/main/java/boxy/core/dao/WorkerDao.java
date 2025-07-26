package boxy.core.dao;

import boxy.core.mapper.SubscriptionOffsetMapper;
import boxy.core.mapper.WorkerMapper;
import boxy.core.model.SubscriptionOffset;
import boxy.core.model.Worker;
import boxy.core.model.WorkerCheckInResult;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Optional;

public final class WorkerDao extends BaseDao {

    private static final WorkerMapper WORKER_MAPPER = new WorkerMapper();
    private static final SubscriptionOffsetMapper SUBSCRIPTION_OFFSET_MAPPER = new SubscriptionOffsetMapper();

    public WorkerDao(DataSource ds) {
        super(ds);
    }

    public long register(String nodeId, long consumerGroupId, int weight) {
        return queryOne("CALL sp_workers_register(?,?,?)", rs -> rs.getLong(1), nodeId, consumerGroupId, weight)
                .orElseThrow();
    }

    public void deregister(long id) {
        update("CALL sp_workers_deregister(?)", id);
    }

    public Optional<Worker> find(long id) {
        return queryOne("SELECT * FROM workers WHERE id = ?",
                WORKER_MAPPER,
                id);
    }

    public Optional<Worker> find(String nodeId, long consumerGroupId) {
        return queryOne("SELECT * FROM workers WHERE node_id = ? AND consumer_group_id = ?",
                WORKER_MAPPER,
                nodeId, consumerGroupId);
    }


    /**
     * Performs a worker check-in, which:
     * 1. Updates the worker's heartbeat
     * 2. Cleans up expired workers
     * 3. Calculates fair share of leases
     * 4. Releases excess leases or acquires new ones as needed
     * 5. Returns statistics and lease changes
     *
     * @param workerId           The ID of the worker checking in
     * @param nodeId             The node identifier
     * @param consumerGroupId    The consumer group ID
     * @param weight             The worker's weight
     * @param leaseTtlMultiplier Multiplier for lease TTL (default: 5)
     * @return A WorkerCheckInResult containing statistics and lease changes
     */
    public WorkerCheckInResult checkIn(long workerId, String nodeId, long consumerGroupId, int weight, int leaseTtlMultiplier) {
        try (final var conn = ds.getConnection();
             final var stmt = conn.prepareCall("CALL sp_workers_check_in(?, ?, ?, ?, ?)")) {

            // Set parameters
            stmt.setLong(1, workerId);
            stmt.setString(2, nodeId);
            stmt.setLong(3, consumerGroupId);
            stmt.setInt(4, weight);
            stmt.setInt(5, leaseTtlMultiplier);

            // Execute the procedure
            final var hasResults = stmt.execute();

            // Process the first result set (statistics)
            if (!hasResults) {
                throw new SQLException("Expected statistics result set not returned");
            }

            final var statsRs = stmt.getResultSet();
            if (!statsRs.next()) {
                throw new SQLException("No statistics returned");
            }

            final var activeWorkers = statsRs.getInt("active_workers");
            final var activePartitions = statsRs.getInt("active_partitions");
            final var totalWeight = statsRs.getInt("total_weight");
            final var workerWeight = statsRs.getInt("worker_weight");
            final var idealShare = statsRs.getDouble("ideal_share");
            final var currentLeases = statsRs.getInt("current_leases");
            final var minLeases = statsRs.getInt("min_leases");
            final var maxLeases = statsRs.getInt("max_leases");
            final var heartbeatInterval = statsRs.getInt("heartbeat_interval");
            final var leaseTtl = statsRs.getInt("lease_ttl");

            statsRs.close();

            // Process the second result set (added leases)
            final var addedLeases = new ArrayList<SubscriptionOffset>();
            if (stmt.getMoreResults()) {
                try (final var addedRs = stmt.getResultSet()) {
                    while (addedRs.next()) {
                        addedLeases.add(SUBSCRIPTION_OFFSET_MAPPER.map(addedRs));
                    }
                }
            }

            // Process the third result set (removed leases)
            final var removedLeases = new ArrayList<SubscriptionOffset>();
            if (stmt.getMoreResults()) {
                try (final var removedRs = stmt.getResultSet()) {
                    while (removedRs.next()) {
                        removedLeases.add(SUBSCRIPTION_OFFSET_MAPPER.map(removedRs));
                    }
                }
            }

            // Create and return the result
            return new WorkerCheckInResult(
                    activeWorkers,
                    activePartitions,
                    totalWeight,
                    workerWeight,
                    idealShare,
                    currentLeases,
                    minLeases,
                    maxLeases,
                    heartbeatInterval,
                    leaseTtl,
                    addedLeases,
                    removedLeases
            );

        } catch (SQLException e) {
            throw new RuntimeException("Error during worker check-in", e);
        }
    }
}
