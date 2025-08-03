CREATE PROCEDURE sp_leases__acquire(
    IN p_worker_id VARCHAR(36),
    IN p_subscription_id BIGINT,
    IN p_leases_to_acquire INT
)
BEGIN
    DECLARE v_random_key INT;
    DECLARE p_limit INT;
    DECLARE v_limit INT;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN RESIGNAL; END;
    SET v_limit = p_leases_to_acquire;

    -- PASS 1: RANDOM PIVOT
    SET v_random_key = fn_random_int();
    INSERT INTO leases (cursor_id, worker_id, state)
         SELECT id, p_worker_id, 'ACTIVE'
           FROM unleased_cursors_view
          WHERE subscription_id = p_subscription_id
            AND random_key >= v_random_key -- Start from a random pivot
      ORDER BY random_key, id
          LIMIT v_limit
             ON DUPLICATE KEY UPDATE
                worker_id = VALUES(worker_id),
                version = leases.version + 1,
                released_at = NULL,
                state = 'ACTIVE';
    SET v_limit = v_limit - ROW_COUNT();


    -- PASS 2: WRAP AROUND FROM THE START
    IF (v_limit > 0) THEN
        INSERT INTO leases (cursor_id, worker_id, state)
             SELECT id, p_worker_id, 'ACTIVE'
               FROM unleased_cursors_view
              WHERE subscription_id = p_subscription_id
                AND random_key < v_random_key
          ORDER BY random_key, id
              LIMIT v_limit
                 ON DUPLICATE KEY UPDATE
                    worker_id = VALUES(worker_id),
                    version = leases.version + 1,
                    released_at = NULL,
                    state = 'ACTIVE';
    END IF;
END;