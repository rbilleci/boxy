CREATE PROCEDURE sp_events__sequence_loop(IN p_batch_size INT)
BEGIN
    DECLARE v_has_events INT DEFAULT 0;
    DECLARE v_no_event_start TIMESTAMP(3) DEFAULT NULL;
    DECLARE v_elapsed_seconds INT DEFAULT 0;

    CREATE TEMPORARY TABLE IF NOT EXISTS temp_claimed_ids (
        id BIGINT PRIMARY KEY,
        partition_id BIGINT NOT NULL
        ) ENGINE = MEMORY;

    main_loop: LOOP
        CALL sp_events__sequence(p_batch_size, v_has_events);

        IF v_has_events = 1 THEN
            SET v_no_event_start = NULL;
            ITERATE main_loop;
        END IF;

        IF v_no_event_start IS NULL THEN
            SET v_no_event_start = CURRENT_TIMESTAMP(3);
        END IF;

        SET v_elapsed_seconds = TIMESTAMPDIFF(SECOND, v_no_event_start, CURRENT_TIMESTAMP(3));

        IF v_elapsed_seconds >= 60 THEN
            LEAVE main_loop;
        END IF;

        DO SLEEP(0.01);
    END LOOP;
END;
