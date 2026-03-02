-- sp_sequence_loop  (v2 — dynamic batch sizing + 1ms idle sleep)
--
-- Outer loop that drives the sequencer.  Called by the MySQL event scheduler
-- every minute (event: sequencer); runs until 60 s of consecutive idle time,
-- then exits so the scheduler can restart it.
--
-- CHANGES FROM v1
--   Dynamic batch sizing (item #40):
--     v1 used a fixed batch size supplied by the caller (always 1000 from the
--     event definition).  During bursts, 1000 events per iteration means
--     50 iterations to drain 50,000 events.
--
--     v2 adjusts the working batch size after each iteration:
--       - If the previous iteration processed a FULL batch (processed == v_batch),
--         the queue is likely deeper; double v_batch (up to MAX_BATCH = 10,000).
--       - If it processed a PARTIAL batch, reset to the minimum (p_batch_size).
--       - If empty, reset to minimum and apply the idle sleep.
--
--     This strategy eliminates the need for a separate COUNT(*) query while
--     still adapting to burst conditions.  The maximum batch size caps memory
--     usage of temp_claimed_ids (MEMORY engine, 10,000 rows × ~24 bytes ≈ 240 KB).
--
--   Reduced idle sleep (item #41):
--     v1 sleeps 10ms (SLEEP(0.01)) between idle iterations, adding up to 10ms
--     of cold-partition latency per cycle.
--     v2 sleeps 1ms (SLEEP(0.001)), improving cold-partition p99 latency by ~9ms
--     at the cost of slightly higher CPU utilisation when idle.
--
-- BENCHMARK GATE
--   - Burst recovery time (drain N=50,000 backlog events) must improve vs v1.
--   - Cold-partition p99 latency must decrease.
--   See docs/benchmarks.md for baseline numbers.
DROP PROCEDURE IF EXISTS sp_sequence_loop;
CREATE PROCEDURE sp_sequence_loop(IN p_batch_size INT)
BEGIN
    DECLARE v_processed       INT DEFAULT 0;
    DECLARE v_batch           INT;
    DECLARE v_no_event_start  TIMESTAMP(3) DEFAULT NULL;
    DECLARE v_elapsed_seconds INT DEFAULT 0;

    -- Constants
    DECLARE v_max_batch INT DEFAULT 10000;

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
