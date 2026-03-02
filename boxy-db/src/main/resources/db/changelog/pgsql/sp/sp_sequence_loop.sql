-- PostgreSQL version note: requires a sp_sequence function to be defined separately
-- This version implements the loop logic from MySQL's sp_sequence_loop_v4

CREATE OR REPLACE FUNCTION sp_sequence_loop(IN p_batch_size INT)
    LANGUAGE plpgsql
AS $$
DECLARE
    v_processed       INT DEFAULT 0;
    v_batch           INT;
    v_no_event_start  TIMESTAMP(3) DEFAULT NULL;
    v_elapsed_seconds INT DEFAULT 0;
    v_min_batch       INT;
    v_max_batch       INT DEFAULT 10000;
BEGIN
    -- Item #92: if caller passes 0 or NULL, read default from boxy_config.
    IF p_batch_size IS NULL OR p_batch_size <= 0 THEN
        SELECT COALESCE(MAX((config_value)::INT), 1000)
          INTO v_min_batch
          FROM boxy_config
         WHERE config_key = 'sequencer.batch.size';
    ELSE
        v_min_batch := p_batch_size;
    END IF;

    -- Initialize working batch to resolved minimum
    v_batch := v_min_batch;

    -- Session-scoped temp table reused across all sp_sequence calls
    CREATE TEMPORARY TABLE IF NOT EXISTS temp_claimed_ids (
        id           BIGINT PRIMARY KEY,
        partition_id BIGINT NOT NULL
    );

    <<main_loop>>
    LOOP
        -- Call sp_sequence to process a batch
        -- Note: sp_sequence must return the number of processed items via OUT parameter
        -- For now, we'll use a placeholder approach
        -- CALL sp_sequence(v_batch, v_processed);

        -- Simplified version: just return without calling sp_sequence
        -- In a real implementation, sp_sequence would be a stored procedure
        -- that processes unprocessed_events and returns the count processed
        v_processed := 0;

        IF v_processed > 0 THEN
            v_no_event_start := NULL;

            IF v_processed >= v_batch THEN
                v_batch := LEAST(v_batch * 2, v_max_batch);
            ELSE
                v_batch := v_min_batch;
            END IF;

            CONTINUE main_loop;
        END IF;

        IF v_no_event_start IS NULL THEN
            v_no_event_start := CURRENT_TIMESTAMP(3);
        END IF;

        v_elapsed_seconds := EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP(3) - v_no_event_start))::INT;

        IF v_elapsed_seconds >= 60 THEN
            EXIT main_loop;
        END IF;

        v_batch := v_min_batch;
        PERFORM pg_sleep(0.001);

    END LOOP;

    DROP TABLE IF EXISTS temp_claimed_ids;
END;
$$;
