CREATE PROCEDURE sp_consumer_groups_update_stats(
    IN p_consumer_group_id BIGINT,
    OUT p_active_workers_count INT,
    OUT p_total_weight INT,
    OUT p_active_partitions_count INT,
    OUT p_heartbeat_interval INT,
    OUT p_lease_ttl_base INT
)
BEGIN
    DECLARE v_stats_exist INT DEFAULT 0;
    DECLARE v_last_updated DATETIME(3);
    DECLARE v_update_threshold DATETIME(3);
    
    -- Set update threshold to 5 seconds ago
    SET v_update_threshold = DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 5 SECOND);
    
    -- Check if stats exist and are recent enough
    SELECT 1, last_updated INTO v_stats_exist, v_last_updated
        FROM consumer_group_stats
        WHERE consumer_group_id = p_consumer_group_id;
    
    -- If stats don't exist or are outdated, recalculate them
    IF v_stats_exist = 0 OR v_last_updated < v_update_threshold THEN
        -- Count active workers and total weight
        SELECT COUNT(*), SUM(weight) 
        INTO p_active_workers_count, p_total_weight
        FROM workers 
        WHERE consumer_group_id = p_consumer_group_id;
        
        -- Count active partitions (where high_watermark > committed_offset)
        SELECT COUNT(*) 
        INTO p_active_partitions_count
        FROM subscription_offsets_view so
        INNER JOIN subscriptions s ON s.id = so.subscription_id
        WHERE s.consumer_group_id = p_consumer_group_id
          AND so.high_watermark > so.committed_offset;
        
        -- Calculate adaptive heartbeat interval
        -- With 1000 workers, we want ~10 check-ins per second
        -- So each worker checks in every ~100 seconds on average
        SET p_heartbeat_interval = GREATEST(3, p_active_workers_count / 10);
        
        -- Set lease TTL base (typically 5x heartbeat interval)
        SET p_lease_ttl_base = p_heartbeat_interval * 5;
        
        -- Insert or update the stats in the table
        INSERT INTO consumer_group_stats (
            consumer_group_id, 
            active_workers_count, 
            total_weight, 
            active_partitions_count, 
            heartbeat_interval, 
            lease_ttl_base, 
            last_updated
        )
        VALUES (
            p_consumer_group_id, 
            p_active_workers_count, 
            p_total_weight, 
            p_active_partitions_count, 
            p_heartbeat_interval, 
            p_lease_ttl_base, 
            CURRENT_TIMESTAMP(3)
        )
        ON DUPLICATE KEY UPDATE 
            active_workers_count = VALUES(active_workers_count),
            total_weight = VALUES(total_weight),
            active_partitions_count = VALUES(active_partitions_count),
            heartbeat_interval = VALUES(heartbeat_interval),
            lease_ttl_base = VALUES(lease_ttl_base),
            last_updated = VALUES(last_updated);
    ELSE
        -- Use existing stats
        SELECT 
            active_workers_count, 
            total_weight, 
            active_partitions_count, 
            heartbeat_interval, 
            lease_ttl_base
        INTO 
            p_active_workers_count, 
            p_total_weight, 
            p_active_partitions_count, 
            p_heartbeat_interval, 
            p_lease_ttl_base
        FROM consumer_group_stats 
        WHERE consumer_group_id = p_consumer_group_id;
    END IF;
END;