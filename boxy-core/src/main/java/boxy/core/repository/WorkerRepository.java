package boxy.core.repository;

import boxy.core.mapper.SubscriptionOffsetMapper;
import boxy.core.mapper.CheckInResultMapper;
import boxy.core.mapper.WorkerMapper;
import boxy.core.domain.Worker;
import boxy.core.domain.CheckInResult;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public final class WorkerRepository extends BaseRepository {

    private static final WorkerMapper WORKER_MAPPER = new WorkerMapper();
    private static final CheckInResultMapper CHECK_IN_RESULT_MAPPER = new CheckInResultMapper();
    private static final SubscriptionOffsetMapper SUBSCRIPTION_OFFSET_MAPPER = new SubscriptionOffsetMapper();

    public WorkerRepository(DataSource ds) {
        super(ds);
    }

    public Optional<Worker> find(String id) {
        return queryOne("SELECT * FROM workers WHERE id = ?", WORKER_MAPPER, id);
    }

    public List<Worker> findAll(int limit, int offset) {
        return query("SELECT * FROM workers ORDER BY id LIMIT ? OFFSET ?", WORKER_MAPPER, limit, offset);
    }

    public void delete(String id) {
        update("{CALL sp_workers__delete(?)}", id);
    }

    public void shutdown(String id) {
        update("{CALL sp_workers__shutdown(?)}", id);
    }


    /**
     * Performs a worker check-in, which:
     * 1. Updates the worker's heartbeat
     * 2. Cleans up expired workers
     * 3. Calculates fair share of leases
     * 4. Releases excess leases or acquires new ones as needed
     * 5. Returns statistics and all active leases
     *
     * @param workerId       The worker identifier
     * @param subscriptionId The subscription ID
     * @param weight         The worker's weight
     * @return A WorkerCheckInResult containing statistics and all active leases
     */
    public CheckInResult checkIn(String workerId,
                                 long subscriptionId,
                                 double weight) {
        try (final var connection = ds.getConnection();
             final var statement = connection.prepareCall("{CALL sp_workers__check_in(?, ?, ?)}")) {
            statement.setString(1, workerId);
            statement.setLong(2, subscriptionId);
            statement.setDouble(3, weight);

            // Execute, then process the results
            final var hasResults = statement.execute();
            if (!hasResults) {
                throw new SQLException("Expected statistics result set not returned");
            }

            try (final var rs = statement.getResultSet()) {
                if (!rs.next()) {
                    throw new SQLException("No statistics found");
                }
                // Map the statistics
                final var workerCheckInResult = CHECK_IN_RESULT_MAPPER.map(rs);
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

}
