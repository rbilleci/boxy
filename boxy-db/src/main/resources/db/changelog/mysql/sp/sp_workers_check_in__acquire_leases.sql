CREATE PROCEDURE sp_workers_check_in__acquire_leases(
    IN p_worker_id VARCHAR(255),
    IN p_consumer_group_id BIGINT,
    IN p_min_leases INT,
    IN p_current_leases INT,
    OUT p_leases_acquired INT
)
BEGIN
    DECLARE v_random_offset BIGINT;
    DECLARE p_limit INT;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN RESIGNAL; END;

    SET p_leases_acquired = 0;

    -- First pass based on a random offset
    IF p_current_leases < p_min_leases THEN
        -- Generate a random offset for randomized scanning
        SELECT FLOOR(RAND() * (SELECT MAX(id) FROM subscription_offsets)) INTO v_random_offset;
        
        -- Find and acquire available leases starting from random offset
        SET p_limit = p_min_leases - p_current_leases;
        
        -- First pass: acquire leases from random offset to end
        INSERT INTO leases (subscription_offset_id, worker_id, state)
             SELECT id, p_worker_id, 'ACTIVE'
               FROM unleased_subscription_offsets_view
              WHERE consumer_group_id = p_consumer_group_id
                AND id >= v_random_offset -- Start from a random offset
           ORDER BY id
              LIMIT p_limit
                 ON DUPLICATE KEY UPDATE
                    worker_id = VALUES(worker_id),
                    version = leases.version + 1,
                    released_at = NULL,
                    state = 'ACTIVE';
        SET p_leases_acquired = ROW_COUNT();
    END IF;

    -- Second pass, based on a random offset (when needed)
    IF (p_leases_acquired + p_current_leases) < p_min_leases THEN
        SET p_limit = p_min_leases - p_current_leases - p_leases_acquired;
        INSERT INTO leases (subscription_offset_id, worker_id, state)
             SELECT id, p_worker_id, 'ACTIVE'
               FROM unleased_subscription_offsets_view
              WHERE consumer_group_id = p_consumer_group_id
                AND id < v_random_offset -- Wrap around, fetching from the start
           ORDER BY id
              LIMIT p_limit
                 ON DUPLICATE KEY UPDATE
                    worker_id = VALUES(worker_id),
                    version = leases.version + 1,
                    released_at = NULL,
                    state = 'ACTIVE';
        SET p_leases_acquired = p_leases_acquired + ROW_COUNT();
    END IF;
END;