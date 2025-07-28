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
     * 5. Returns statistics and all active leases
     *
     * @param nodeId             The node identifier
     * @param consumerGroupId    The consumer group ID
     * @param weight             The worker's weight
     * @return A WorkerCheckInResult containing statistics and all active leases
     */
    public WorkerCheckInResult checkIn(String nodeId,
                                       long consumerGroupId,
                                       int weight) {
        try (final var connection = ds.getConnection();
             final var statement = connection.prepareCall("CALL sp_workers_check_in(?, ?, ?)")) {
            statement.setString(1, nodeId);
            statement.setLong(2, consumerGroupId);
            statement.setInt(3, weight);

            // Execute, then process the results
            final var hasResults = statement.execute();
            if (!hasResults) {
                throw new SQLException("Expected statistics result set not returned");
            }

            //
            try (final var rs = statement.getResultSet()) {
                if (!rs.next()) {
                    throw new SQLException("No statistics found");
                }
                // Map the statistics
                final var workerCheckInResult = WORKER_CHECK_IN_RESULT_MAPPER.map(rs);
                // Record active leases
                if (statement.getMoreResults()) {
                    try (final var leasesRs = statement.getResultSet()) {
                        while (leasesRs.next()) {
                            workerCheckInResult.activeLeases().add(SUBSCRIPTION_OFFSET_MAPPER.map(leasesRs));
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
