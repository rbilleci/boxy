CREATE PROCEDURE sp_leases_acquire_needed(
    IN p_worker_id BIGINT,
    IN p_consumer_group_id BIGINT,
    IN p_min_leases INT,
    IN p_current_leases INT,
    IN p_lease_ttl INT,
    OUT p_leases_acquired INT
)
BEGIN
    DECLARE v_random_offset BIGINT;
    DECLARE p_limit INT;
    
    SET p_leases_acquired = 0;
    
    -- If we have too few leases, grab more
    IF p_current_leases < p_min_leases THEN
        -- Generate a random offset for randomized scanning
        SELECT FLOOR(RAND() * (SELECT MAX(id) FROM subscription_offsets)) INTO v_random_offset;
        
        -- Find available leases starting from random offset
        SET p_limit = p_min_leases - p_current_leases;
        INSERT INTO temp_leases_added
        SELECT lav.id
        FROM leases_available_view lav
        INNER JOIN subscriptions s ON s.id = lav.subscription_id
        WHERE s.consumer_group_id = p_consumer_group_id
          AND lav.id >= v_random_offset
          AND lav.high_watermark > lav.committed_offset
        ORDER BY lav.id
        LIMIT p_limit;
        
        -- If we didn't get enough, wrap around to the beginning
        SET p_limit = p_min_leases - p_current_leases - (SELECT COUNT(*) FROM temp_leases_added);
        IF (SELECT COUNT(*) FROM temp_leases_added) < (p_min_leases - p_current_leases) THEN
            INSERT INTO temp_leases_added
            SELECT lav.id
            FROM leases_available_view lav
            INNER JOIN subscriptions s ON s.id = lav.subscription_id
            WHERE s.consumer_group_id = p_consumer_group_id
              AND lav.id < v_random_offset
              AND lav.high_watermark > lav.committed_offset
              AND lav.id NOT IN (SELECT subscription_offset_id FROM temp_leases_added)
            ORDER BY lav.id
            LIMIT p_limit;
        END IF;
        
        -- Acquire the selected leases
        INSERT INTO leases (subscription_offset_id, worker_id, state)
        SELECT subscription_offset_id, p_worker_id, 'ACTIVE'
        FROM temp_leases_added
        ON DUPLICATE KEY UPDATE
            worker_id = VALUES(worker_id),
            version = leases.version + 1,
            released_at = NULL,
            state = 'ACTIVE';
        
        -- Count acquired leases
        SELECT COUNT(*) INTO p_leases_acquired FROM temp_leases_added;
    END IF;
END;