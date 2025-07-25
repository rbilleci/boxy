CREATE PROCEDURE sp_workers_check_in(
    IN p_worker_id BIGINT,
    IN p_node_id VARCHAR(255),
    IN p_consumer_group_id BIGINT,
    IN p_weight INT,
    IN p_lease_ttl_multiplier INT DEFAULT 5
)
BEGIN
    DECLARE v_total_weight INT DEFAULT 0;
    DECLARE v_active_partitions INT DEFAULT 0;
    DECLARE v_current_leases INT DEFAULT 0;
    DECLARE v_ideal_share DECIMAL(10,2);
    DECLARE v_slack DECIMAL(10,2) DEFAULT 0.1; -- 10% slack to avoid thrashing
    DECLARE v_min_leases INT;
    DECLARE v_max_leases INT;
    DECLARE v_active_workers INT DEFAULT 0;
    DECLARE v_heartbeat_interval INT;
    DECLARE v_lease_ttl INT;
    DECLARE v_random_offset BIGINT;
    DECLARE v_batch_size INT DEFAULT 10; -- Number of leases to grab/release at once
    
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
    INSERT INTO workers (id, node_id, consumer_group_id, weight, last_heartbeat)
    VALUES (p_worker_id, p_node_id, p_consumer_group_id, p_weight, CURRENT_TIMESTAMP(3))
    ON DUPLICATE KEY UPDATE 
        weight = VALUES(weight),
        last_heartbeat = VALUES(last_heartbeat);
    
    -- 2. Clean up expired workers and their leases
    DELETE FROM workers 
    WHERE consumer_group_id = p_consumer_group_id 
      AND last_heartbeat < DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 10 SECOND);
    
    -- 3. Calculate fair share
    -- Count active workers and total weight
    SELECT COUNT(*), SUM(weight) 
    INTO v_active_workers, v_total_weight
    FROM workers 
    WHERE consumer_group_id = p_consumer_group_id;
    
    -- Count active partitions (where high_watermark > committed_offset)
    SELECT COUNT(*) 
    INTO v_active_partitions
    FROM subscription_offsets_view so
    INNER JOIN subscriptions s ON s.id = so.subscription_id
    WHERE s.consumer_group_id = p_consumer_group_id
      AND so.high_watermark > so.committed_offset;
    
    -- Count current leases for this worker
    SELECT COUNT(*) 
    INTO v_current_leases
    FROM leases l
    WHERE l.worker_id = p_worker_id
      AND l.expires_at > CURRENT_TIMESTAMP(3);
    
    -- Calculate ideal share based on weight
    IF v_total_weight > 0 AND v_active_partitions > 0 THEN
        SET v_ideal_share = (p_weight / v_total_weight) * v_active_partitions;
    ELSE
        SET v_ideal_share = 0;
    END IF;
    
    -- Calculate min and max leases with slack
    SET v_min_leases = FLOOR(v_ideal_share - v_slack);
    SET v_max_leases = CEILING(v_ideal_share + v_slack);
    
    -- Ensure min_leases is not negative
    IF v_min_leases < 0 THEN
        SET v_min_leases = 0;
    END IF;
    
    -- 4. Calculate adaptive heartbeat interval and lease TTL
    -- With 1000 workers, we want ~10 check-ins per second
    -- So each worker checks in every ~100 seconds on average
    SET v_heartbeat_interval = GREATEST(3, v_active_workers / 10);
    SET v_lease_ttl = v_heartbeat_interval * p_lease_ttl_multiplier;
    
    -- 5. Work-stealing logic
    -- If we have too many leases, release some
    IF v_current_leases > v_max_leases THEN
        -- Find leases to release (smallest backlog first)
        INSERT INTO temp_leases_removed
        SELECT l.subscription_offset_id
        FROM leases l
        INNER JOIN subscription_offsets_view so ON so.id = l.subscription_offset_id
        INNER JOIN subscriptions s ON s.id = so.subscription_id
        WHERE l.worker_id = p_worker_id
          AND s.consumer_group_id = p_consumer_group_id
        ORDER BY (so.high_watermark - so.committed_offset) ASC
        LIMIT v_current_leases - v_max_leases;
        
        -- Release the selected leases
        DELETE FROM leases
        WHERE worker_id = p_worker_id
          AND subscription_offset_id IN (SELECT subscription_offset_id FROM temp_leases_removed);
    END IF;
    
    -- If we have too few leases, grab more
    IF v_current_leases < v_min_leases THEN
        -- Generate a random offset for randomized scanning
        SELECT FLOOR(RAND() * (SELECT MAX(id) FROM subscription_offsets)) INTO v_random_offset;
        
        -- Find available leases starting from random offset
        INSERT INTO temp_leases_added
        SELECT lav.id
        FROM leases_available_view lav
        INNER JOIN subscriptions s ON s.id = lav.subscription_id
        WHERE s.consumer_group_id = p_consumer_group_id
          AND lav.id >= v_random_offset
          AND lav.high_watermark > lav.committed_offset
        ORDER BY lav.id
        LIMIT v_min_leases - v_current_leases;
        
        -- If we didn't get enough, wrap around to the beginning
        IF (SELECT COUNT(*) FROM temp_leases_added) < (v_min_leases - v_current_leases) THEN
            INSERT INTO temp_leases_added
            SELECT lav.id
            FROM leases_available_view lav
            INNER JOIN subscriptions s ON s.id = lav.subscription_id
            WHERE s.consumer_group_id = p_consumer_group_id
              AND lav.id < v_random_offset
              AND lav.high_watermark > lav.committed_offset
              AND lav.id NOT IN (SELECT subscription_offset_id FROM temp_leases_added)
            ORDER BY lav.id
            LIMIT v_min_leases - v_current_leases - (SELECT COUNT(*) FROM temp_leases_added);
        END IF;
        
        -- Acquire the selected leases
        INSERT INTO leases (subscription_offset_id, worker_id, expires_at)
        SELECT subscription_offset_id, p_worker_id, DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL v_lease_ttl SECOND)
        FROM temp_leases_added
        ON DUPLICATE KEY UPDATE
            worker_id = IF(leases.expires_at < CURRENT_TIMESTAMP(3), VALUES(worker_id), leases.worker_id),
            version = IF(leases.expires_at < CURRENT_TIMESTAMP(3), leases.version + 1, leases.version),
            expires_at = IF(leases.expires_at < CURRENT_TIMESTAMP(3), VALUES(expires_at), leases.expires_at);
    END IF;
    
    COMMIT;
    
    -- 6. Return results
    -- Return stats for the worker to calculate next check-in time
    SELECT 
        v_active_workers AS active_workers,
        v_active_partitions AS active_partitions,
        v_total_weight AS total_weight,
        p_weight AS worker_weight,
        v_ideal_share AS ideal_share,
        v_current_leases AS current_leases,
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