CREATE PROCEDURE sp_workers__gc(IN p_subscription_id BIGINT)
BEGIN
    DECLARE v_worker_id VARCHAR(255);
    DECLARE done INT DEFAULT FALSE;

    -- CURSOR / DELETE: OLDEST HEARTBEAT FIRST
    DECLARE worker_cursor CURSOR FOR
        SELECT id
          FROM workers
         WHERE CURRENT_TIMESTAMP(3) >= heartbeat_deadline
           AND workers.subscription_id = p_subscription_id
      ORDER BY heartbeat_deadline
         LIMIT 10;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN RESIGNAL; END;

    -- LEASES / GARBAGE COLLECTION
    DELETE leases
      FROM leases
           JOIN subscription_offsets ON subscription_offsets.id = leases.subscription_offset_id
           JOIN subscription_topics st ON st.id = subscription_offsets.subscription_id
          WHERE state = 'RELEASING'
            AND CURRENT_TIMESTAMP(3) >= release_deadline
            AND st.subscription_id = p_subscription_id;

    -- WORKERS / GARBAGE COLLECTION
    OPEN worker_cursor;
        read_loop: LOOP
            FETCH worker_cursor INTO v_worker_id;
            IF done THEN
                LEAVE read_loop;
            END IF;
            CALL sp_workers__delete(v_worker_id);
        END LOOP;
    CLOSE worker_cursor;

END;