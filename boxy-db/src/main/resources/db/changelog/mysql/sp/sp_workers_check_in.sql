CREATE PROCEDURE sp_workers_check_in(
    IN p_worker_id BIGINT,
    IN p_node_id VARCHAR(255),
    IN p_consumer_group_id BIGINT,
    IN p_weight INT,
    IN p_lease_ttl_multiplier INT
)
BEGIN
    DECLARE v_active_workers_count INT DEFAULT 0;
    DECLARE v_total_weight INT DEFAULT 0;
    DECLARE v_active_partitions_count INT DEFAULT 0;
    DECLARE v_heartbeat_interval INT;
    DECLARE v_lease_ttl_base INT;
    DECLARE v_lease_ttl INT;
    DECLARE v_current_leases INT DEFAULT 0;
    DECLARE v_ideal_share DECIMAL(10,2);
    DECLARE v_slack DECIMAL(10,2) DEFAULT 0.1; -- 10% slack to avoid thrashing
    DECLARE v_min_leases INT;
    DECLARE v_max_leases INT;
    DECLARE v_leases_released INT DEFAULT 0;
    DECLARE v_leases_acquired INT DEFAULT 0;
    
    -- Temporary tables for tracking lease changes
    CREATE TEMPORARY TABLE IF NOT EXISTS temp_leases_added (
        subscription_offset_id BIGINT PRIMARY KEY
    );
    
    CREATE TEMPORARY TABLE IF NOT EXISTS temp_leases_removed (
        subscription_offset_id BIGINT PRIMARY KEY
    );
    
    TRUNCATE temp_leases_added;
    TRUNCATE temp_leases_removed;
    
    -- Start transaction to ensure consistency
    START TRANSACTION;
    
    -- 1. Update worker heartbeat
    CALL sp_workers_update_heartbeat(p_worker_id, p_node_id, p_consumer_group_id, p_weight);
    
    -- 2. Clean up expired workers and their leases
    CALL sp_workers_cleanup_expired(p_consumer_group_id, 10);
    
    -- 2.1 Clean up leases in RELEASING state where the worker's last_updated timestamp is more than 10-seconds old
    DELETE FROM leases
    WHERE state = 'RELEASING'
      AND released_at < CURRENT_TIMESTAMP(3) - INTERVAL 10 SECOND;
    
    -- 3. Get or calculate consumer group statistics
    CALL sp_consumer_groups_update_stats(
        p_consumer_group_id,
        v_active_workers_count,
        v_total_weight,
        v_active_partitions_count,
        v_heartbeat_interval,
        v_lease_ttl_base
    );
    
    -- 4. Calculate lease TTL using the multiplier
    SET v_lease_ttl = v_lease_ttl_base * p_lease_ttl_multiplier;
    
    -- 5. Count current leases for this worker
    -- Only count ACTIVE leases, not RELEASING ones
    SELECT COUNT(*) 
    INTO v_current_leases
    FROM leases l
    WHERE l.worker_id = p_worker_id
      AND l.state = 'ACTIVE';
    
    -- 6. Calculate ideal share based on weight
    IF v_total_weight > 0 AND v_active_partitions_count > 0 THEN
        SET v_ideal_share = (p_weight / v_total_weight) * v_active_partitions_count;
    ELSE
        SET v_ideal_share = 0;
    END IF;
    
    -- 7. Calculate min and max leases with slack
    SET v_min_leases = FLOOR(v_ideal_share - v_slack);
    SET v_max_leases = CEILING(v_ideal_share + v_slack);
    
    -- Ensure min_leases is not negative
    IF v_min_leases < 0 THEN
        SET v_min_leases = 0;
    END IF;
    
    -- 8. Work-stealing logic
    -- If we have too many leases, release some
    CALL sp_leases_release_excess(
        p_worker_id,
        p_consumer_group_id,
        v_max_leases,
        v_current_leases,
        v_leases_released
    );
    
    -- If we have too few leases, grab more
    CALL sp_leases_acquire_needed(
        p_worker_id,
        p_consumer_group_id,
        v_min_leases,
        v_current_leases - v_leases_released,
        v_lease_ttl,
        v_leases_acquired
    );
    
    COMMIT;
    
    -- 9. Return results
    -- Return stats for the worker to calculate next check-in time
    SELECT 
        v_active_workers_count AS active_workers,
        v_active_partitions_count AS active_partitions,
        v_total_weight AS total_weight,
        p_weight AS worker_weight,
        v_ideal_share AS ideal_share,
        v_current_leases - v_leases_released + v_leases_acquired AS current_leases,
        v_min_leases AS min_leases,
        v_max_leases AS max_leases,
        v_heartbeat_interval AS heartbeat_interval,
        v_lease_ttl AS lease_ttl;
    
    -- Return added leases
    SELECT 
        so.id AS subscription_offset_id,
        so.subscription_id,
        so.partition_id,
        so.committed_offset,
        so.high_watermark
    FROM temp_leases_added tla
    INNER JOIN subscription_offsets_view so ON so.id = tla.subscription_offset_id;
    
    -- Return removed leases
    SELECT 
        so.id AS subscription_offset_id,
        so.subscription_id,
        so.partition_id,
        so.committed_offset,
        so.high_watermark
    FROM temp_leases_removed tlr
    INNER JOIN subscription_offsets_view so ON so.id = tlr.subscription_offset_id;
    
    -- Clean up temporary tables
    DROP TEMPORARY TABLE IF EXISTS temp_leases_added;
    DROP TEMPORARY TABLE IF EXISTS temp_leases_removed;
END;