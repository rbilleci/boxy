package boxy.core.dao;

import boxy.core.mapper.SubscriptionOffsetMapper;
import boxy.core.mapper.WorkerCheckInResultMapper;
import boxy.core.mapper.WorkerMapper;
import boxy.core.model.Worker;
import boxy.core.model.WorkerCheckInResult;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Optional;

public final class WorkerDao extends BaseDao {

    private static final WorkerMapper WORKER_MAPPER = new WorkerMapper();
    private static final WorkerCheckInResultMapper WORKER_CHECK_IN_RESULT_MAPPER = new WorkerCheckInResultMapper();
    private static final SubscriptionOffsetMapper SUBSCRIPTION_OFFSET_MAPPER = new SubscriptionOffsetMapper();

    public WorkerDao(DataSource ds) {
        super(ds);
    }

    public Optional<Worker> find(long id) {
        return queryOne("SELECT * FROM workers WHERE id = ?", WORKER_MAPPER, id);
    }

    public Optional<Worker> find(String nodeId, long consumerGroupId) {
        return queryOne("SELECT * FROM workers WHERE node_id = ? AND consumer_group_id = ?",
                WORKER_MAPPER, nodeId, consumerGroupId);
    }


    /**
     * Performs a worker check-in, which:
     * 1. Updates the worker's heartbeat
     * 2. Cleans up expired workers
     * 3. Calculates fair share of leases
     * 4. Releases excess leases or acquires new ones as needed
     * 5. Returns statistics and lease changes
     *
     * @param nodeId             The node identifier
     * @param consumerGroupId    The consumer group ID
     * @param weight             The worker's weight
     * @param leaseTtlMultiplier Multiplier for lease TTL (default: 5)
     * @return A WorkerCheckInResult containing statistics and lease changes
     */
    public WorkerCheckInResult checkIn(String nodeId,
                                       long consumerGroupId,
                                       int weight,
                                       int leaseTtlMultiplier) {
        try (final var conn = ds.getConnection();
             final var stmt = conn.prepareCall("CALL sp_workers_check_in(?, ?, ?, ?)")) {
            stmt.setString(1, nodeId);
            stmt.setLong(2, consumerGroupId);
            stmt.setInt(3, weight);
            stmt.setInt(4, leaseTtlMultiplier);

            // Execute, then process the results
            final var hasResults = stmt.execute();
            if (!hasResults) {
                throw new SQLException("Expected statistics result set not returned");
            }

            //
            try (final var rs = stmt.getResultSet()) {
                if (!rs.next()) {
                    throw new SQLException("No statistics found");
                }
                // Map the statistics
                final var workerCheckInResult = WORKER_CHECK_IN_RESULT_MAPPER.map(rs);
                // Record added leases
                if (stmt.getMoreResults()) {
                    try (final var addedRs = stmt.getResultSet()) {
                        while (addedRs.next()) {
                            workerCheckInResult.addedLeases().add(SUBSCRIPTION_OFFSET_MAPPER.map(addedRs));
                        }
                    }
                }
                // Record removed leases
                if (stmt.getMoreResults()) {
                    try (final var removedRs = stmt.getResultSet()) {
                        while (removedRs.next()) {
                            workerCheckInResult.removedLeases().add(SUBSCRIPTION_OFFSET_MAPPER.map(removedRs));
                        }
                    }
                }
                return workerCheckInResult;
            }


        } catch (SQLException e) {
            throw new RuntimeException("Error during worker check-in", e);
        }
    }


    public void shutdown(long id) {
        update("CALL sp_workers_shutdown(?)", id);
    }

}
