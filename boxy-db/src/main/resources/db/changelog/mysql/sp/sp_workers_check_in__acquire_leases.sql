CREATE PROCEDURE sp_workers_check_in__acquire_leases(
    IN p_worker_id BIGINT,
    IN p_consumer_group_id BIGINT,
    IN p_min_leases INT,
    IN p_current_leases INT,
    OUT p_leases_acquired INT
)
BEGIN
    DECLARE v_random_offset BIGINT;
    DECLARE p_limit INT;
    DECLARE v_acquired_first_pass INT DEFAULT 0;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN RESIGNAL; END;
    
    SET p_leases_acquired = 0;
    
    -- If we have too few leases, grab more
    IF p_current_leases < p_min_leases THEN
        -- Generate a random offset for randomized scanning
        SELECT FLOOR(RAND() * (SELECT MAX(id) FROM subscription_offsets)) INTO v_random_offset;
        
        -- Find and acquire available leases starting from random offset
        SET p_limit = p_min_leases - p_current_leases;
        
        -- First pass: acquire leases from random offset to end
        INSERT INTO leases (subscription_offset_id, worker_id, state)
        SELECT lav.id, p_worker_id, 'ACTIVE'
        FROM leases_available_view lav
        INNER JOIN subscriptions s ON s.id = lav.subscription_id
        WHERE s.consumer_group_id = p_consumer_group_id
          AND lav.id >= v_random_offset
          AND lav.high_watermark > lav.committed_offset
        ORDER BY lav.id
        LIMIT p_limit
        ON DUPLICATE KEY UPDATE
            worker_id = VALUES(worker_id),
            version = leases.version + 1,
            released_at = NULL,
            state = 'ACTIVE';
        
        -- Count acquired leases in first pass
        SET v_acquired_first_pass = ROW_COUNT();
        SET p_leases_acquired = v_acquired_first_pass;
        
        -- If we didn't get enough, wrap around to the beginning
        SET p_limit = p_min_leases - p_current_leases - v_acquired_first_pass;
        IF v_acquired_first_pass < (p_min_leases - p_current_leases) THEN
            -- Second pass: acquire leases from beginning to random offset
            INSERT INTO leases (subscription_offset_id, worker_id, state)
            SELECT lav.id, p_worker_id, 'ACTIVE'
            FROM leases_available_view lav
            INNER JOIN subscriptions s ON s.id = lav.subscription_id
            LEFT JOIN leases l ON l.subscription_offset_id = lav.id AND l.worker_id = p_worker_id
            WHERE s.consumer_group_id = p_consumer_group_id
              AND lav.id < v_random_offset
              AND lav.high_watermark > lav.committed_offset
              AND l.subscription_offset_id IS NULL
            ORDER BY lav.id
            LIMIT p_limit
            ON DUPLICATE KEY UPDATE
                worker_id = VALUES(worker_id),
                version = leases.version + 1,
                released_at = NULL,
                state = 'ACTIVE';
            
            -- Add second pass acquired leases to total
            SET p_leases_acquired = p_leases_acquired + ROW_COUNT();
        END IF;
    END IF;
END;