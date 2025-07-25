package boxy.core.dao;

import boxy.core.model.SubscriptionOffset;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;
import java.util.Random;

public final class SubscriptionOffsetDao extends BaseDao {

    private static final RowMapper<SubscriptionOffset> MAPPER = rs -> new SubscriptionOffset(
            rs.getLong("id"),
            rs.getLong("subscription_id"),
            rs.getLong("partition_id"),
            rs.getLong("committed_offset"),
            rs.getLong("high_watermark"));
            
    private final Random random = new Random();

    public SubscriptionOffsetDao(DataSource ds) {
        super(ds);
    }

    public void commit(long id, long offset) {
        update("CALL sp_commit_offset(?,?)", id, offset);
    }

    /**
     * Retrieves available leases from a specific offset.
     *
     * @param limit Maximum number of leases to retrieve
     * @param offset Starting offset
     * @return List of available subscription offsets
     */
    public List<SubscriptionOffset> leasesAvailable(int limit, int offset) {
        return query("SELECT * FROM leases_available_view LIMIT ? OFFSET ?", MAPPER, limit, offset);
    }
    
    /**
     * Retrieves available leases for a specific consumer group.
     *
     * @param consumerGroupId The consumer group ID
     * @param limit Maximum number of leases to retrieve
     * @return List of available subscription offsets
     */
    public List<SubscriptionOffset> leasesAvailableForConsumerGroup(long consumerGroupId, int limit) {
        return query(
            "SELECT lav.* FROM leases_available_view lav " +
            "JOIN subscription_offsets so ON so.id = lav.id " +
            "JOIN subscriptions s ON s.id = so.subscription_id " +
            "WHERE s.consumer_group_id = ? " +
            "LIMIT ?", 
            MAPPER, consumerGroupId, limit);
    }
    
    /**
     * Retrieves available leases starting from a random offset.
     * This is used for the randomized scan in the work-stealing algorithm.
     *
     * @param consumerGroupId The consumer group ID
     * @param limit Maximum number of leases to retrieve
     * @return List of available subscription offsets
     */
    public List<SubscriptionOffset> leasesAvailableRandomPivot(long consumerGroupId, int limit) {
        // First, count the total number of available leases for this consumer group
        Integer count = queryOne(
            "SELECT COUNT(*) FROM leases_available_view lav " +
            "JOIN subscription_offsets so ON so.id = lav.id " +
            "JOIN subscriptions s ON s.id = so.subscription_id " +
            "WHERE s.consumer_group_id = ?",
            rs -> rs.getInt(1), consumerGroupId).orElse(0);
            
        if (count == 0) {
            return List.of();
        }
        
        // Generate a random offset within the range of available leases
        int randomOffset = count <= limit ? 0 : random.nextInt(count - limit + 1);
        
        // Query with the random offset
        return query(
            "SELECT lav.* FROM leases_available_view lav " +
            "JOIN subscription_offsets so ON so.id = lav.id " +
            "JOIN subscriptions s ON s.id = so.subscription_id " +
            "WHERE s.consumer_group_id = ? " +
            "LIMIT ? OFFSET ?", 
            MAPPER, consumerGroupId, limit, randomOffset);
    }
    
    /**
     * Counts the number of active partitions for a consumer group.
     * Active partitions are those where high_watermark > committed_offset.
     *
     * @param consumerGroupId The consumer group ID
     * @return The number of active partitions
     */
    public int countActivePartitions(long consumerGroupId) {
        return queryOne(
            "SELECT COUNT(*) FROM subscription_offsets_view sov " +
            "JOIN subscriptions s ON s.id = sov.subscription_id " +
            "WHERE s.consumer_group_id = ? AND sov.high_watermark > sov.committed_offset",
            rs -> rs.getInt(1), consumerGroupId).orElse(0);
    }

    public Optional<SubscriptionOffset> find(long id) {
        return queryOne("SELECT * FROM subscription_offsets_view WHERE id = ?", MAPPER, id);
    }

    public Optional<SubscriptionOffset> find(long subscriptionId, long partitionId) {
        return queryOne("SELECT * FROM subscription_offsets_view WHERE subscription_id = ? AND partition_id = ?", MAPPER, subscriptionId, partitionId);
    }

    public List<SubscriptionOffset> findAll(long subscriptionId) {
        return query("SELECT * FROM subscription_offsets_view WHERE subscription_id = ? ORDER BY id", MAPPER, subscriptionId);
    }
}
