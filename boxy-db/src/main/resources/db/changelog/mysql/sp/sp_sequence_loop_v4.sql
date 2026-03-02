-- sp_sequence_loop  (v4 — configurable default batch size from boxy_config)
--
-- === Item #92: Make sequencer batch size configurable ===
--   v3 used p_batch_size directly from the caller.  When p_batch_size is 0,
--   v4 reads 'sequencer.batch.size' from boxy_config, falling back to 1000.
--
--   The MySQL event scheduler calls sp_sequence_loop(0), so the effective
--   default is controlled via boxy_config without redeploying the event:
--     UPDATE boxy_config SET config_value = '2000'
--      WHERE config_key = 'sequencer.batch.size';
--
--   All other behaviour is identical to v3 (EXIT HANDLER, dynamic batch
--   sizing, 1ms idle sleep).
DROP PROCEDURE IF EXISTS sp_sequence_loop;
CREATE PROCEDURE sp_sequence_loop(IN p_batch_size INT)
BEGIN
    DECLARE v_processed       INT DEFAULT 0;
    DECLARE v_batch           INT;
    DECLARE v_no_event_start  TIMESTAMP(3) DEFAULT NULL;
    DECLARE v_elapsed_seconds INT DEFAULT 0;
    DECLARE v_min_batch       INT;

    -- Constants
    DECLARE v_max_batch INT DEFAULT 10000;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        DROP TEMPORARY TABLE IF EXISTS temp_claimed_ids;
        RESIGNAL;
    END;

    -- Item #92: if caller passes 0, read default from boxy_config.
    IF p_batch_size IS NULL OR p_batch_size <= 0 THEN
        SELECT COALESCE(MAX(CAST(config_value AS SIGNED)), 1000)
          INTO v_min_batch
          FROM boxy_config
         WHERE config_key = 'sequencer.batch.size';
    ELSE
        SET v_min_batch = p_batch_size;
    END IF;

    -- Initialise working batch to resolved minimum
    SET v_batch = v_min_batch;

    -- Session-scoped temp table reused across all sp_sequence calls
    CREATE TEMPORARY TABLE IF NOT EXISTS temp_claimed_ids (
        id           BIGINT PRIMARY KEY,
        partition_id BIGINT NOT NULL
    ) ENGINE = MEMORY;

    main_loop: LOOP

        CALL sp_sequence(v_batch, v_processed);

        IF v_processed > 0 THEN
            SET v_no_event_start = NULL;

            IF v_processed >= v_batch THEN
                SET v_batch = LEAST(v_batch * 2, v_max_batch);
            ELSE
                SET v_batch = v_min_batch;
            END IF;

            ITERATE main_loop;
        END IF;

        IF v_no_event_start IS NULL THEN
            SET v_no_event_start = CURRENT_TIMESTAMP(3);
        END IF;

        SET v_elapsed_seconds = TIMESTAMPDIFF(SECOND, v_no_event_start, CURRENT_TIMESTAMP(3));

        IF v_elapsed_seconds >= 60 THEN
            LEAVE main_loop;
        END IF;

        SET v_batch = v_min_batch;
        DO SLEEP(0.001);

    END LOOP;

    DROP TEMPORARY TABLE IF EXISTS temp_claimed_ids;
END;
