-- sp_sequence_loop  (v3 — EXIT HANDLER for SQLEXCEPTION)
--
-- === Item #75: Stored procedure error handling ===
--   Adds DECLARE EXIT HANDLER FOR SQLEXCEPTION so that unexpected errors during
--   sequencer loop execution surface as SQL exceptions to the caller (MySQL event
--   scheduler) rather than being silently swallowed.
--
--   On error: DROP the temp_claimed_ids table (cleanup) then RESIGNAL so the
--   MySQL event scheduler logs the error and can alert operators.
--
--   All other behaviour is identical to v2.
DROP PROCEDURE IF EXISTS sp_sequence_loop;
CREATE PROCEDURE sp_sequence_loop(IN p_batch_size INT)
BEGIN
    DECLARE v_processed       INT DEFAULT 0;
    DECLARE v_batch           INT;
    DECLARE v_no_event_start  TIMESTAMP(3) DEFAULT NULL;
    DECLARE v_elapsed_seconds INT DEFAULT 0;

    -- Constants
    DECLARE v_max_batch INT DEFAULT 10000;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        DROP TEMPORARY TABLE IF EXISTS temp_claimed_ids;
        RESIGNAL;
    END;

    -- Initialise working batch to caller-supplied minimum
    SET v_batch = p_batch_size;

    -- Session-scoped temp table reused across all sp_sequence calls
    CREATE TEMPORARY TABLE IF NOT EXISTS temp_claimed_ids (
        id           BIGINT PRIMARY KEY,
        partition_id BIGINT NOT NULL
    ) ENGINE = MEMORY;

    main_loop: LOOP

        CALL sp_sequence(v_batch, v_processed);

        IF v_processed > 0 THEN
            -- ---------------------------------------------------------------
            -- Events were processed; reset idle timer.
            -- Adapt batch size: if we got a full batch, the queue is likely
            -- deeper — double up to the cap.  If partial, a burst just ended;
            -- reset to the configured minimum for fair scheduling.
            -- ---------------------------------------------------------------
            SET v_no_event_start = NULL;

            IF v_processed >= v_batch THEN
                -- Full batch: increase aggressively to drain faster
                SET v_batch = LEAST(v_batch * 2, v_max_batch);
            ELSE
                -- Partial batch: burst has ended, reset to minimum
                SET v_batch = p_batch_size;
            END IF;

            ITERATE main_loop;
        END IF;

        -- -------------------------------------------------------------------
        -- No events this iteration: manage idle timeout and sleep.
        -- -------------------------------------------------------------------
        IF v_no_event_start IS NULL THEN
            SET v_no_event_start = CURRENT_TIMESTAMP(3);
        END IF;

        SET v_elapsed_seconds = TIMESTAMPDIFF(SECOND, v_no_event_start, CURRENT_TIMESTAMP(3));

        IF v_elapsed_seconds >= 60 THEN
            -- Idle for 60 s; exit so the scheduler can reschedule this event.
            -- This avoids a single long-running session accumulating memory
            -- or blocking schema changes.
            LEAVE main_loop;
        END IF;

        -- Reset batch size during idle periods
        SET v_batch = p_batch_size;

        -- 1ms sleep (down from 10ms in v1) to reduce cold-partition latency
        DO SLEEP(0.001);

    END LOOP;

    DROP TEMPORARY TABLE IF EXISTS temp_claimed_ids;
END;
