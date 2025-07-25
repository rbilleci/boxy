package boxy.core.dao;

import boxy.core.model.SubscriptionOffset;
import boxy.core.model.Worker;
import boxy.core.model.WorkerCheckInResult;

import javax.sql.DataSource;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class WorkerDao extends BaseDao {

    private static final RowMapper<Worker> MAPPER = rs -> new Worker(
            rs.getLong("id"),
            rs.getString("node_id"),
            rs.getLong("consumer_group_id"),
            rs.getInt("weight"),
            rs.getTimestamp("last_heartbeat").toInstant());

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
        return queryOne("SELECT * FROM workers WHERE id = ?", MAPPER, id);
    }

    public Optional<Worker> find(String nodeId, long consumerGroupId) {
        return queryOne("SELECT * FROM workers WHERE node_id = ? AND consumer_group_id = ?", MAPPER, nodeId, consumerGroupId);
    }
    
    /**
     * Row mapper for subscription offsets returned by the check-in procedure
     */
    private static final RowMapper<SubscriptionOffset> SUBSCRIPTION_OFFSET_MAPPER = rs -> new SubscriptionOffset(
            rs.getLong("subscription_offset_id"),
            rs.getLong("subscription_id"),
            rs.getLong("partition_id"),
            rs.getLong("committed_offset"),
            rs.getLong("high_watermark"));
    
    /**
     * Performs a worker check-in, which:
     * 1. Updates the worker's heartbeat
     * 2. Cleans up expired workers
     * 3. Calculates fair share of leases
     * 4. Releases excess leases or acquires new ones as needed
     * 5. Returns statistics and lease changes
     *
     * @param workerId The ID of the worker checking in
     * @param nodeId The node identifier
     * @param consumerGroupId The consumer group ID
     * @param weight The worker's weight
     * @param leaseTtlMultiplier Multiplier for lease TTL (default: 5)
     * @return A WorkerCheckInResult containing statistics and lease changes
     */
    public WorkerCheckInResult checkIn(long workerId, String nodeId, long consumerGroupId, int weight, int leaseTtlMultiplier) {
        try (Connection conn = ds.getConnection();
             CallableStatement stmt = conn.prepareCall("CALL sp_workers_check_in(?, ?, ?, ?, ?)")) {
            
            // Set parameters
            stmt.setLong(1, workerId);
            stmt.setString(2, nodeId);
            stmt.setLong(3, consumerGroupId);
            stmt.setInt(4, weight);
            stmt.setInt(5, leaseTtlMultiplier);
            
            // Execute the procedure
            boolean hasResults = stmt.execute();
            
            // Process the first result set (statistics)
            if (!hasResults) {
                throw new SQLException("Expected statistics result set not returned");
            }
            
            ResultSet statsRs = stmt.getResultSet();
            if (!statsRs.next()) {
                throw new SQLException("No statistics returned");
            }
            
            int activeWorkers = statsRs.getInt("active_workers");
            int activePartitions = statsRs.getInt("active_partitions");
            int totalWeight = statsRs.getInt("total_weight");
            int workerWeight = statsRs.getInt("worker_weight");
            double idealShare = statsRs.getDouble("ideal_share");
            int currentLeases = statsRs.getInt("current_leases");
            int minLeases = statsRs.getInt("min_leases");
            int maxLeases = statsRs.getInt("max_leases");
            int heartbeatInterval = statsRs.getInt("heartbeat_interval");
            int leaseTtl = statsRs.getInt("lease_ttl");
            
            statsRs.close();
            
            // Process the second result set (added leases)
            List<SubscriptionOffset> addedLeases = new ArrayList<>();
            if (stmt.getMoreResults()) {
                try (ResultSet addedRs = stmt.getResultSet()) {
                    while (addedRs.next()) {
                        addedLeases.add(SUBSCRIPTION_OFFSET_MAPPER.map(addedRs));
                    }
                }
            }
            
            // Process the third result set (removed leases)
            List<SubscriptionOffset> removedLeases = new ArrayList<>();
            if (stmt.getMoreResults()) {
                try (ResultSet removedRs = stmt.getResultSet()) {
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
