-- PostgreSQL version of sp_events__poll
-- Note: PostgreSQL doesn't support multiple result sets from a single function.
-- This version returns events as a table. Callers should fetch polling_probability
-- separately via sp_events__poll_metadata or include it in the result set.

CREATE TYPE poll_result_row AS (
    cursor_id BIGINT,
    partition_id BIGINT,
    sequence BIGINT,
    event_id BIGINT,
    data BYTEA
);

CREATE TYPE poll_metadata_result AS (
    polling_probability DOUBLE PRECISION
);

CREATE OR REPLACE FUNCTION sp_events__poll(
    IN p_consumer_id VARCHAR(36),
    IN p_batch_size  INT)
    RETURNS SETOF poll_result_row
    LANGUAGE plpgsql
AS $$
DECLARE
    v_start_key       INT     DEFAULT fn_random_int();
    v_now             TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3);
    v_subscription_id BIGINT;
    v_topic_ids       JSONB;
    v_batch_size      INT;
    v_polling_prob    DOUBLE PRECISION;
    v_lease_lock_sec  INT DEFAULT 3;
BEGIN
    -- Apply batch size: clamp to [1, 10000]; 0 or negative → default 100.
    v_batch_size := CASE 
                      WHEN p_batch_size IS NULL OR p_batch_size <= 0 THEN 100
                      ELSE LEAST(p_batch_size, 10000)
                    END;

    -- ------------------------------------------------------------------
    -- Step 0: Look up consumer.
    -- ------------------------------------------------------------------
    SELECT subscription_id, topic_ids INTO v_subscription_id, v_topic_ids
      FROM consumers
     WHERE id = p_consumer_id;

    IF v_subscription_id IS NULL THEN
        RAISE EXCEPTION 'UNKNOWN_CONSUMER';
    END IF;

    -- ------------------------------------------------------------------
    -- Step 0b: Read configurable lease lock duration (item #90).
    -- Falls back to 3s if boxy_config row is absent.
    -- ------------------------------------------------------------------
    SELECT COALESCE(MAX((config_value)::INT), 3)
      INTO v_lease_lock_sec
      FROM boxy_config
     WHERE config_key = 'lease.lock.seconds';

    -- ------------------------------------------------------------------
    -- Step 1: Heartbeat — update detected_at and deadline.
    -- ------------------------------------------------------------------
    UPDATE consumers
       SET heartbeat_detected_at = v_now,
           heartbeat_deadline    = v_now + (heartbeat_interval || ' seconds')::INTERVAL
     WHERE id = p_consumer_id;

    -- ------------------------------------------------------------------
    -- Step 2 (item #44): Refresh active_consumers in subscription_topics.
    -- ------------------------------------------------------------------
    UPDATE subscription_topics
       SET active_consumers = (
               SELECT COUNT(*)
                 FROM consumers c2
                WHERE c2.subscription_id = v_subscription_id
                  AND c2.heartbeat_deadline > v_now
           )
     WHERE subscription_id = v_subscription_id;

    -- ------------------------------------------------------------------
    -- Step 3 (item #43): Compute adaptive polling_probability.
    -- ------------------------------------------------------------------
    SELECT LEAST(1.0,
                 1.0 / (AVG(st.heartbeat_interval) *
                        GREATEST(MAX(st.active_consumers), 1)))
      INTO v_polling_prob
      FROM subscription_topics st
     WHERE st.subscription_id = v_subscription_id;

    -- ------------------------------------------------------------------
    -- Step 4: Candidate selection.
    -- ------------------------------------------------------------------
    CREATE TEMPORARY TABLE tmp_selected_sequences (
        cursor_id    BIGINT,
        partition_id BIGINT,
        sequence     BIGINT,
        event_id     BIGINT,
        PRIMARY KEY (cursor_id, sequence)
    );

    INSERT INTO tmp_selected_sequences (cursor_id, partition_id, sequence, event_id)
    WITH candidate AS (
        SELECT
            c.id AS cursor_id,
            c.partition_id,
            s.sequence,
            s.event_id,
            ROW_NUMBER() OVER (
                ORDER BY (c.random_key >= v_start_key) DESC, c.random_key, c.id, s.sequence
            ) AS row_num
        FROM cursors c
        JOIN partitions p ON p.id = c.partition_id
        LEFT JOIN consumer_leases l ON l.cursor_id = c.id
        JOIN LATERAL (
            SELECT s.sequence, s.event_id
            FROM sequences s
            WHERE s.partition_id = c.partition_id
              AND s.sequence > COALESCE(
                  CASE WHEN l.consumer_id = p_consumer_id
                       THEN l.last_read_position
                       ELSE NULL
                  END,
                  c.position)
            ORDER BY s.sequence
            LIMIT v_batch_size
        ) s ON TRUE
        WHERE c.subscription_id = v_subscription_id
          AND v_topic_ids @> jsonb_build_array(c.topic_id)
          AND (
              l.cursor_id IS NULL OR
              l.locked_until IS NULL OR
              l.locked_until < v_now OR
              l.consumer_id = p_consumer_id
          )
          AND p.high_watermark > c.position
    )
    SELECT cursor_id, partition_id, sequence, event_id
      FROM candidate
     WHERE row_num <= v_batch_size;

    -- ------------------------------------------------------------------
    -- Step 5: Acquire leases (item #90: v_lease_lock_sec from boxy_config).
    -- ------------------------------------------------------------------
    INSERT INTO consumer_leases (cursor_id)
    SELECT c.id
      FROM cursors c
      JOIN tmp_selected_sequences sel ON sel.cursor_id = c.id
    ON CONFLICT (cursor_id) DO NOTHING;

    UPDATE consumer_leases l
       SET consumer_id  = p_consumer_id,
           locked_until = v_now + (v_lease_lock_sec || ' seconds')::INTERVAL,
           last_read_position = CASE
               WHEN l.consumer_id = p_consumer_id THEN COALESCE(l.last_read_position, c.position)
               ELSE c.position
           END
      FROM tmp_selected_sequences sel
      JOIN cursors c ON c.id = l.cursor_id
     WHERE sel.cursor_id = l.cursor_id
       AND (l.locked_until IS NULL OR l.locked_until < v_now OR l.consumer_id = p_consumer_id);

    -- ------------------------------------------------------------------
    -- Step 6: Advance last_read_position.
    -- ------------------------------------------------------------------
    UPDATE consumer_leases l
       SET last_read_position = GREATEST(COALESCE(l.last_read_position, 0), sel.last_sequence)
      FROM (
          SELECT cursor_id, MAX(sequence) AS last_sequence
            FROM tmp_selected_sequences
           GROUP BY cursor_id
      ) sel
     WHERE sel.cursor_id = l.cursor_id
       AND l.consumer_id = p_consumer_id;

    -- ------------------------------------------------------------------
    -- Step 7: Return events (result set).
    -- ------------------------------------------------------------------
    RETURN QUERY
    SELECT
        sel.cursor_id,
        c.partition_id,
        sel.sequence,
        e.id   AS event_id,
        e.data
      FROM tmp_selected_sequences sel
      JOIN cursors c ON c.id = sel.cursor_id
      JOIN consumer_leases l
        ON l.cursor_id = sel.cursor_id
       AND l.consumer_id = p_consumer_id
      JOIN events e
        ON e.id = sel.event_id;

    DROP TABLE IF EXISTS tmp_selected_sequences;
END;
$$;

-- Separate function for polling metadata
CREATE OR REPLACE FUNCTION sp_events__poll_metadata(
    IN p_subscription_id BIGINT)
    RETURNS SETOF poll_metadata_result
    LANGUAGE plpgsql
    STABLE
AS $$
DECLARE
    v_polling_prob DOUBLE PRECISION;
BEGIN
    SELECT LEAST(1.0,
                 1.0 / (AVG(st.heartbeat_interval) *
                        GREATEST(MAX(st.active_consumers), 1)))
      INTO v_polling_prob
      FROM subscription_topics st
     WHERE st.subscription_id = p_subscription_id;

    RETURN QUERY SELECT COALESCE(v_polling_prob, 1.0)::DOUBLE PRECISION;
END;
$$;
