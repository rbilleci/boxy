CREATE PROCEDURE sp_workers_check_in__update_stats(
    IN p_consumer_group_id BIGINT,
    OUT p_active_workers_count INT,
    OUT p_total_weight INT,
    OUT p_active_partitions_count INT,
    OUT p_heartbeat_interval DOUBLE
)
BEGIN
    DECLARE v_last_updated DATETIME(3);
    DECLARE v_update_threshold DATETIME(3);
    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN RESIGNAL; END;
    
    -- Set update threshold to 1 seconds ago
    SET v_update_threshold = DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 1 SECOND);
    
    -- Check if stats are recent enough
    SELECT last_updated INTO v_last_updated FROM consumer_groups WHERE id = p_consumer_group_id;
    
    -- If stats are outdated, recalculate them
    IF v_last_updated < v_update_threshold THEN
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
        
        -- Get heartbeat_interval_default from consumer_groups
        SELECT heartbeat_interval_default 
          INTO p_heartbeat_interval
          FROM consumer_groups
         WHERE id = p_consumer_group_id;
        
        -- Calculate adaptive heartbeat interval based on heartbeat_interval_default
        -- With 1000 workers, we want ~10 check-ins per second
        -- So each worker checks in every ~100 seconds on average
        SET p_heartbeat_interval = GREATEST(p_heartbeat_interval, p_active_workers_count / 10.0);

        -- Update the stats in the consumer_groups table
        UPDATE consumer_groups
           SET active_workers_count = p_active_workers_count,
              total_weight = p_total_weight,
              active_partitions_count = p_active_partitions_count,
              last_updated = CURRENT_TIMESTAMP(3)
        WHERE id = p_consumer_group_id;
    ELSE
        -- Use existing stats for counts and weights
        SELECT 
            active_workers_count, 
            total_weight, 
            active_partitions_count,
            heartbeat_interval_default
        INTO 
            p_active_workers_count, 
            p_total_weight, 
            p_active_partitions_count,
            p_heartbeat_interval
        FROM consumer_groups 
        WHERE id = p_consumer_group_id;
        
        -- Calculate adaptive heartbeat interval based on heartbeat_interval_default
        SET p_heartbeat_interval = GREATEST(p_heartbeat_interval, p_active_workers_count / 10.0);
    END IF;
END;