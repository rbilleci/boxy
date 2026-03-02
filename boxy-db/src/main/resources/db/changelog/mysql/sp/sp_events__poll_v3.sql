-- sp_events__poll  (v3 — EXIT HANDLER for SQLEXCEPTION)
--
-- === Item #79: Stored procedure error handling ===
--   Adds DECLARE EXIT HANDLER FOR SQLEXCEPTION.
--   On error: DROP the tmp_selected_sequences temp table (cleanup) then
--   RESIGNAL so the caller receives the original SQL exception rather than
--   a partial result.
--
--   All other behaviour is identical to v2 (adaptive polling_probability,
--   stats update, configurable batch size).
DROP PROCEDURE IF EXISTS sp_events__poll;
CREATE PROCEDURE sp_events__poll(
    IN p_consumer_id VARCHAR(36),
    IN p_batch_size  INT
)
BEGIN
    DECLARE v_start_key       INT     DEFAULT fn_random_int();
    DECLARE v_now             DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3);
    DECLARE v_subscription_id BIGINT;
    DECLARE v_topic_ids       JSON;
    DECLARE v_batch_size      INT;
    DECLARE v_polling_prob    DOUBLE;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        DROP TEMPORARY TABLE IF EXISTS tmp_selected_sequences;
        RESIGNAL;
    END;

    -- Apply batch size: clamp to [1, 10000]; 0 or negative → default 100.
    SET v_batch_size = IF(p_batch_size IS NULL OR p_batch_size <= 0,
                          100,
                          LEAST(p_batch_size, 10000));

    -- ------------------------------------------------------------------
    -- Step 0: Look up consumer.
    -- ------------------------------------------------------------------
    SELECT subscription_id, topic_ids INTO v_subscription_id, v_topic_ids
      FROM consumers
     WHERE id = p_consumer_id;

    IF v_subscription_id IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'UNKNOWN_CONSUMER';
    END IF;

    -- ------------------------------------------------------------------
    -- Step 1: Heartbeat — update detected_at and deadline.
    -- ------------------------------------------------------------------
    UPDATE consumers
       SET heartbeat_detected_at = v_now,
           heartbeat_deadline    = DATE_ADD(v_now, INTERVAL heartbeat_interval SECOND)
     WHERE id = p_consumer_id;

    -- ------------------------------------------------------------------
    -- Step 2 (item #44): Refresh active_consumers in subscription_topics.
    -- Counts consumers with a non-expired heartbeat_deadline.
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
    -- Formula: p = min(1, 1 / (heartbeat_interval * max(active_consumers, 1)))
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
    DROP TEMPORARY TABLE IF EXISTS tmp_selected_sequences;
    CREATE TEMPORARY TABLE tmp_selected_sequences (
        cursor_id    BIGINT,
        partition_id BIGINT,
        sequence     BIGINT,
        event_id     BIGINT,
        PRIMARY KEY (cursor_id, sequence)
    ) ENGINE = MEMORY;

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
            FROM sequences s FORCE INDEX (idx_sequences__partition_sequence)
            WHERE s.partition_id = c.partition_id
              AND s.sequence > IF(
                  l.consumer_id = p_consumer_id,
                  COALESCE(l.last_read_position, c.position),
                  c.position)
            ORDER BY s.sequence
        ) s ON TRUE
        WHERE c.subscription_id = v_subscription_id
          AND JSON_CONTAINS(v_topic_ids, CAST(c.topic_id AS JSON), '$')
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
    -- Step 5: Acquire leases.
    -- ------------------------------------------------------------------
    INSERT INTO consumer_leases (cursor_id)
    SELECT c.id
      FROM cursors c
      JOIN tmp_selected_sequences sel ON sel.cursor_id = c.id
    ON DUPLICATE KEY UPDATE
        cursor_id = consumer_leases.cursor_id;

    UPDATE consumer_leases l
      JOIN tmp_selected_sequences sel ON sel.cursor_id = l.cursor_id
      JOIN cursors c ON c.id = l.cursor_id
       SET l.consumer_id  = p_consumer_id,
           l.locked_until = DATE_ADD(v_now, INTERVAL 3 SECOND),
           l.last_read_position = CASE
               WHEN l.consumer_id = p_consumer_id THEN COALESCE(l.last_read_position, c.position)
               ELSE c.position
           END
     WHERE l.locked_until IS NULL OR l.locked_until < v_now OR l.consumer_id = p_consumer_id;

    -- ------------------------------------------------------------------
    -- Step 6: Advance last_read_position.
    -- ------------------------------------------------------------------
    UPDATE consumer_leases l
      JOIN (
          SELECT cursor_id, MAX(sequence) AS last_sequence
            FROM tmp_selected_sequences
           GROUP BY cursor_id
      ) sel ON sel.cursor_id = l.cursor_id
       SET l.last_read_position = GREATEST(IFNULL(l.last_read_position, 0), sel.last_sequence)
     WHERE l.consumer_id = p_consumer_id;

    -- ------------------------------------------------------------------
    -- Step 7: Return events (result set 1).
    -- ------------------------------------------------------------------
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

    -- ------------------------------------------------------------------
    -- Step 8: Return metadata (result set 2) with adaptive probability.
    -- ------------------------------------------------------------------
    SELECT COALESCE(v_polling_prob, 1.0) AS polling_probability;

    DROP TEMPORARY TABLE IF EXISTS tmp_selected_sequences;
END;
