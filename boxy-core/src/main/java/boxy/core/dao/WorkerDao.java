package boxy.core.dao;

import boxy.core.DataAccessException;
import boxy.core.model.Worker;

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
     * Performs a worker check-in, which updates the worker's heartbeat, manages leases,
     * and returns statistics about the consumer group and the worker's lease allocation.
     *
     * @param workerId The ID of the worker checking in
     * @param weight The worker's current weight
     * @param heartbeatInterval The interval in seconds between heartbeats
     * @param batchSize The maximum number of leases to acquire in a single check-in
     * @return A WorkerCheckInResult containing statistics and lease changes
     */
    public WorkerCheckInResult checkIn(long workerId, int weight, int heartbeatInterval, int batchSize) {
        try (Connection conn = ds.getConnection();
             CallableStatement stmt = conn.prepareCall("CALL sp_workers_check_in(?,?,?,?)")) {
            
            stmt.setLong(1, workerId);
            stmt.setInt(2, weight);
            stmt.setInt(3, heartbeatInterval);
            stmt.setInt(4, batchSize);
            
            boolean hasResults = stmt.execute();
            if (!hasResults) {
                throw new SQLException("Expected results from sp_workers_check_in");
            }
            
            // First result set contains statistics
            ResultSet statsRs = stmt.getResultSet();
            if (!statsRs.next()) {
                throw new SQLException("Expected statistics in first result set");
            }
            
            WorkerCheckInResult result = new WorkerCheckInResult(
                    statsRs.getLong("consumer_group_id"),
                    statsRs.getInt("total_weight"),
                    statsRs.getInt("active_partitions"),
                    statsRs.getDouble("ideal_share"),
                    statsRs.getInt("current_leases"),
                    statsRs.getInt("lease_expiry_seconds")
            );
            
            // Second result set contains lease changes
            if (stmt.getMoreResults()) {
                List<LeaseChange> leaseChanges = new ArrayList<>();
                ResultSet leaseRs = stmt.getResultSet();
                
                while (leaseRs.next()) {
                    leaseChanges.add(new LeaseChange(
                            leaseRs.getLong("subscription_offset_id"),
                            leaseRs.getString("status"),
                            leaseRs.getLong("partition_id")
                    ));
                }
                
                result.setLeaseChanges(leaseChanges);
            }
            
            return result;
            
        } catch (SQLException e) {
            throw new DataAccessException(e);
        }
    }
    
    /**
     * Represents the result of a worker check-in operation.
     */
    public static class WorkerCheckInResult {
        private final long consumerGroupId;
        private final int totalWeight;
        private final int activePartitions;
        private final double idealShare;
        private final int currentLeases;
        private final int leaseExpirySeconds;
        private List<LeaseChange> leaseChanges = new ArrayList<>();
        
        public WorkerCheckInResult(long consumerGroupId, int totalWeight, int activePartitions, 
                                  double idealShare, int currentLeases, int leaseExpirySeconds) {
            this.consumerGroupId = consumerGroupId;
            this.totalWeight = totalWeight;
            this.activePartitions = activePartitions;
            this.idealShare = idealShare;
            this.currentLeases = currentLeases;
            this.leaseExpirySeconds = leaseExpirySeconds;
        }
        
        public void setLeaseChanges(List<LeaseChange> leaseChanges) {
            this.leaseChanges = leaseChanges;
        }
        
        public long getConsumerGroupId() {
            return consumerGroupId;
        }
        
        public int getTotalWeight() {
            return totalWeight;
        }
        
        public int getActivePartitions() {
            return activePartitions;
        }
        
        public double getIdealShare() {
            return idealShare;
        }
        
        public int getCurrentLeases() {
            return currentLeases;
        }
        
        public int getLeaseExpirySeconds() {
            return leaseExpirySeconds;
        }
        
        public List<LeaseChange> getLeaseChanges() {
            return leaseChanges;
        }
    }
    
    /**
     * Represents a change to a lease (added or releasing).
     */
    public static class LeaseChange {
        private final long subscriptionOffsetId;
        private final String status;
        private final long partitionId;
        
        public LeaseChange(long subscriptionOffsetId, String status, long partitionId) {
            this.subscriptionOffsetId = subscriptionOffsetId;
            this.status = status;
            this.partitionId = partitionId;
        }
        
        public long getSubscriptionOffsetId() {
            return subscriptionOffsetId;
        }
        
        public String getStatus() {
            return status;
        }
        
        public long getPartitionId() {
            return partitionId;
        }
        
        public boolean isReleasing() {
            return "RELEASING".equals(status);
        }
    }
}
