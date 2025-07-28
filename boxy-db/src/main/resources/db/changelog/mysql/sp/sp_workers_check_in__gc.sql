CREATE PROCEDURE sp_workers_check_in__gc(IN p_consumer_group_id BIGINT)
BEGIN
    DECLARE v_release_deadline INT;
    DECLARE v_worker_id VARCHAR(255);
    DECLARE done INT DEFAULT FALSE;

    -- Cursor for processing dead workers. We limit to 10 per check-in.
    DECLARE worker_cursor CURSOR FOR
        SELECT id
          FROM workers
         WHERE CURRENT_TIMESTAMP(3) >= heartbeat_deadline
      ORDER BY heartbeat_deadline
         LIMIT 10;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;

    -- Exit Handler
    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN RESIGNAL; END;

    -- Fetch the release deadline for this consumer group
    SELECT release_deadline
      INTO v_release_deadline
      FROM consumer_groups
     WHERE id = p_consumer_group_id;

    -- GC: Delete leases that are stuck in a 'releasing' state.
    -- Set a limit to minimize the impact on the system,
    -- depending on future check-ins to complete the process.
    DELETE FROM leases
          WHERE state = 'RELEASING'
            AND CURRENT_TIMESTAMP(3) >= (released_at + INTERVAL v_release_deadline SECOND)
       ORDER BY released_at
          LIMIT 32;

    -- GC: Remove workers past their heartbeat deadline
    OPEN worker_cursor;
        read_loop: LOOP
            FETCH worker_cursor INTO v_worker_id;
            IF done THEN
                LEAVE read_loop;
            END IF;
            CALL sp_workers_delete(v_worker_id);
        END LOOP;
    CLOSE worker_cursor;

END;