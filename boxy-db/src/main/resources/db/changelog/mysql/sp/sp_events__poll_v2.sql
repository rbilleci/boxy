-- sp_events__poll  (v2 — adaptive polling_probability, stats update, configurable batch)
--
-- Polls events for a registered consumer and returns up to p_batch_size event rows
-- plus a metadata row with the adaptive polling_probability.
--
-- Signature change from v1:
--   + IN p_batch_size INT  — desired batch size; 0 or negative uses the default of 100.
--                            Clamped to [1, 10000]. (item #49)
--
-- RESULT SETS
--   1. Events result set (may be empty):
--        cursor_id, partition_id, sequence, event_id, data
--   2. Metadata result set (always one row):
--        polling_probability DOUBLE
--
-- ALGORITHM
--   0. Look up consumer → subscription + topic_ids.  SIGNAL on unknown consumer.
--   1. Heartbeat update for this consumer.
--   2. Update active_consumers in subscription_topics (item #44).
--   3. Compute adaptive polling_probability from subscription statistics (item #43).
--   4. Candidate selection using a LATERAL JOIN + ROW_NUMBER window.
--        FORCE INDEX hint retained — see item #47 note below.
--        JSON_CONTAINS filter retained — see item #48 note below.
--   5. Lease acquisition: INSERT ON DUPLICATE KEY + UPDATE (see items #45/#46 notes).
--   6. Advance last_read_position in consumer_leases.
--   7. Return event rows from the events table.
--   8. Return metadata row with adaptive polling_probability.
--
-- ITEM #44 — Stats update
--   active_consumers is updated on every poll call: COUNT of consumers for this
--   subscription whose heartbeat_deadline has not expired.  This is cheap (PK
--   lookup on consumers.subscription_id index + date filter).
--   active_partitions update is deferred to a future background scheduled procedure
--   to avoid adding a full cursor scan to the poll hot path.
--
-- ITEM #43 — Adaptive polling_probability
--   Formula:  p = min(1, 1 / (heartbeat_interval * max(active_consumers, 1)))
--   Interpretation: with N active consumers and a heartbeat interval H seconds,
--   each consumer should poll at rate 1 / (H * N) to produce a combined rate of
--   1 / H polls/second per subscription — matching the expected message delivery SLA.
--   Falls back to 1.0 if no subscription_topics rows exist (always poll).
--
-- ITEM #47 — FORCE INDEX hint
--   The LATERAL subquery `SELECT sequence, event_id FROM sequences WHERE
--   partition_id = c.partition_id AND sequence > ... ORDER BY sequence` needs the
--   (partition_id, sequence) covering index to perform an efficient range scan.
--   Without FORCE INDEX the optimizer sometimes chooses a full table scan on large
--   sequences tables.  TODO: replace with a covering index hint in the WHERE clause
--   ordering and verify with EXPLAIN after sequences has >1M rows.
--
-- ITEM #48 — JSON_CONTAINS topic filter
--   `JSON_CONTAINS(v_topic_ids, CAST(c.topic_id AS JSON), '$')` is evaluated per
--   cursor row.  For consumers with ≤ 20 topics this is fast (single JSON parse).
--   For > 20 topics, consider materialising topic_ids into a temp table and joining.
--   Benchmark gate: poll latency with 20+ topics must exceed current baseline before
--   implementing; measure using BenchmarkIT.poll_throughput with a high-topic consumer.
--
-- ITEM #45 / #46 — Lease acquisition and contention
--   The current two-step approach (INSERT ON DUPLICATE KEY + UPDATE) has a short race
--   window between the candidate CTE and the lease UPDATE.  SELECT … FOR UPDATE SKIP
--   LOCKED would eliminate the window but requires restructuring the CTE into a
--   separate locking step.  Deferred until bench-cloud-small contention numbers show
--   > 5% throughput loss vs. an uncontended baseline.
--
-- ITEM #50 — Pre-computed cursor counts
--   The `p.high_watermark > c.position` filter is evaluated per cursor.  A future
--   has_pending flag on cursors (updated by the sequencer) would skip inactive cursors
--   without scanning.  Deferred until poll throughput with 1000+ cursors (90% idle)
--   shows measurable degradation in bench-cloud benchmarks.
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
