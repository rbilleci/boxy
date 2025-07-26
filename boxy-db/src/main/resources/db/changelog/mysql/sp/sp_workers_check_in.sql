CREATE PROCEDURE sp_workers_check_in(
    IN p_worker_id BIGINT,
    IN p_weight INT,
    IN p_heartbeat_interval INT,
    IN p_batch_size INT
)
BEGIN
    DECLARE v_consumer_group_id BIGINT;
    DECLARE v_total_weight INT;
    DECLARE v_active_partitions INT;
    DECLARE v_ideal_share DECIMAL(10,2);
    DECLARE v_current_leases INT;
    DECLARE v_lease_expiry_multiplier INT DEFAULT 5; -- Lease expiry is 5x heartbeat interval
    DECLARE v_lease_expiry_seconds INT;
    DECLARE v_random_offset BIGINT;
    
    -- Start transaction to ensure consistency
    START TRANSACTION;
    
    -- Get the consumer group ID for this worker
    SELECT consumer_group_id INTO v_consumer_group_id 
    FROM workers 
    WHERE id = p_worker_id;
    
    -- Update worker's heartbeat and weight
    UPDATE workers 
    SET last_heartbeat = CURRENT_TIMESTAMP(3), weight = p_weight 
    WHERE id = p_worker_id;
    
    -- Calculate lease expiry in seconds (5x heartbeat interval)
    SET v_lease_expiry_seconds = p_heartbeat_interval * v_lease_expiry_multiplier;
    
    -- Clean up RELEASING leases where release started more than 10 seconds ago
    DELETE FROM leases 
    WHERE status = 'RELEASING' 
    AND release_started_at < DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 10 SECOND);
    
    -- Clean up expired workers and their leases
    -- First, identify expired workers (last_heartbeat older than lease expiry time)
    CREATE TEMPORARY TABLE IF NOT EXISTS expired_workers AS
    SELECT id 
    FROM workers 
    WHERE last_heartbeat < DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL v_lease_expiry_seconds SECOND);
    
    -- Delete leases for expired workers
    DELETE FROM leases 
    WHERE worker_id IN (SELECT id FROM expired_workers);
    
    -- Delete expired workers
    DELETE FROM workers 
    WHERE id IN (SELECT id FROM expired_workers);
    
    -- Drop temporary table
    DROP TEMPORARY TABLE IF EXISTS expired_workers;
    
    -- Calculate total weight of all active workers in the consumer group
    SELECT COALESCE(SUM(weight), 0) INTO v_total_weight 
    FROM workers 
    WHERE consumer_group_id = v_consumer_group_id 
    AND last_heartbeat >= DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL v_lease_expiry_seconds SECOND);
    
    -- Calculate number of active partitions (high_watermark > committed_offset)
    SELECT COUNT(*) INTO v_active_partitions 
    FROM subscription_offsets so
    INNER JOIN partitions p ON p.id = so.partition_id
    INNER JOIN subscriptions s ON s.id = so.subscription_id
    WHERE s.consumer_group_id = v_consumer_group_id
    AND p.high_watermark > so.committed_offset;
    
    -- Calculate ideal share for this worker
    IF v_total_weight > 0 THEN
        SET v_ideal_share = (p_weight / v_total_weight) * v_active_partitions;
    ELSE
        SET v_ideal_share = v_active_partitions;
    END IF;
    
    -- Count current active leases for this worker
    SELECT COUNT(*) INTO v_current_leases 
    FROM leases 
    WHERE worker_id = p_worker_id 
    AND status = 'ACTIVE';
    
    -- If worker has more leases than ideal share + 1, release some
    IF v_current_leases > FLOOR(v_ideal_share) + 1 THEN
        -- Mark excess leases as RELEASING
        UPDATE leases
        JOIN (
            SELECT subscription_offset_id
            FROM leases
            WHERE worker_id = p_worker_id
            AND status = 'ACTIVE'
            ORDER BY subscription_offset_id
            LIMIT (v_current_leases - FLOOR(v_ideal_share))
        ) AS to_release ON leases.subscription_offset_id = to_release.subscription_offset_id
        SET leases.status = 'RELEASING',
            leases.release_started_at = CURRENT_TIMESTAMP(3);
    -- If worker has fewer leases than ideal share - 1, grab more
    ELSEIF v_current_leases < CEILING(v_ideal_share) - 1 AND v_active_partitions > 0 THEN
        -- Generate a random offset for fair distribution
        SET v_random_offset = FLOOR(RAND() * v_active_partitions);
        
        -- Acquire new leases
        INSERT INTO leases (subscription_offset_id, worker_id, status)
        SELECT so.id, p_worker_id, 'ACTIVE'
        FROM (
            SELECT so.id
            FROM subscription_offsets so
            INNER JOIN partitions p ON p.id = so.partition_id
            INNER JOIN subscriptions s ON s.id = so.subscription_id
            LEFT JOIN leases l ON l.subscription_offset_id = so.id AND l.status = 'ACTIVE'
            WHERE s.consumer_group_id = v_consumer_group_id
            AND p.high_watermark > so.committed_offset
            AND l.subscription_offset_id IS NULL
            ORDER BY so.id
            LIMIT p_batch_size
            OFFSET v_random_offset
        ) AS so
        ON DUPLICATE KEY UPDATE
            worker_id = VALUES(worker_id),
            version = leases.version + 1,
            status = 'ACTIVE',
            release_started_at = NULL
        LIMIT (CEILING(v_ideal_share) - v_current_leases);
    END IF;
    
    -- Return statistics to the worker
    SELECT 
        v_consumer_group_id AS consumer_group_id,
        v_total_weight AS total_weight,
        v_active_partitions AS active_partitions,
        v_ideal_share AS ideal_share,
        v_current_leases AS current_leases,
        v_lease_expiry_seconds AS lease_expiry_seconds;
    
    -- Return the list of leases that were added or removed
    SELECT 
        l.subscription_offset_id,
        l.status,
        so.partition_id
    FROM leases l
    JOIN subscription_offsets so ON so.id = l.subscription_offset_id
    WHERE l.worker_id = p_worker_id
    AND (l.status = 'RELEASING' OR l.acquired_at > DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 5 SECOND));
    
    -- Commit the transaction
    COMMIT;
END;