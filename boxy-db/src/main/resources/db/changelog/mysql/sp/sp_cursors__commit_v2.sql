-- sp_cursors__commit  (v2 — lease release on commit, evaluation docs)
--
-- === Item #51: Benchmark commit throughput ceiling ===
--   Benchmark gate: BenchmarkIT.commit_throughput() measures sp_cursors__commit
--   call rate with repeated idempotent commits of a single cursor.
--   See: docs/benchmarks.md §Commit Path Evaluation.
--
-- === Item #52: Evaluate batched cursor UPDATE without temp table ===
--   Hypothesis: for small batches (1–10 cursors), CREATE TEMPORARY TABLE +
--   INSERT + JOIN UPDATE exceeds the cost of direct per-row UPDATEs.
--
--   Evaluation:
--   - The MEMORY-engine temp table creation in MySQL 8.0 is ~0.05–0.1 ms per call
--     (single-threaded; negligible at low concurrency).
--   - For typical client batch sizes (1–5 cursors), a direct multi-row UPDATE
--     via a VALUES() clause or per-cursor UPDATE loop would reduce overhead.
--   - However, the JSON input format (map of cursor_id→position) makes a threshold
--     approach complex: we would need to count JSON keys before choosing a path.
--   - JSON_TABLE + temp table is correct and readable; the overhead is
--     immaterial until commit throughput exceeds ~50K calls/sec on a single node.
--
--   Decision: retain temp table path. Revisit if bench-cloud-prod shows commit
--   throughput < 10K calls/sec; at that point implement a threshold of ≤ 5 cursors
--   → direct UPDATE, > 5 → temp table.
--   See: docs/benchmarks.md §Commit Path Evaluation.
--
-- === Item #53: Release leases on commit ===
--   Previously, consumer_leases expired after lock_duration (~3s) even after a
--   successful commit.  This caused unnecessary lease contention: other consumers
--   could not acquire the partition for up to 3s after the previous consumer
--   committed.
--
--   Change: after updating cursor positions, immediately NULL out locked_until
--   for the committed cursors owned by this consumer.  This releases the partition
--   for immediate re-acquisition by any consumer.
--
--   Cost: one additional UPDATE on consumer_leases (index lookup on cursor_id +
--   consumer_id).  For a batch of N cursors, this is O(N) index rows — the same
--   cardinality as the cursor UPDATE above.
--
--   Correctness: we only release leases for the committing consumer.  A lease held
--   by a different consumer (edge case: consumer crashed mid-lease and another
--   took over) is not touched.

DROP PROCEDURE IF EXISTS sp_cursors__commit;
CREATE PROCEDURE sp_cursors__commit(
    IN p_consumer_id    VARCHAR(36),
    IN p_cursor_positions JSON)
BEGIN
    DECLARE v_subscription_id BIGINT;
    DECLARE v_input_count     INT DEFAULT 0;
    DECLARE v_updated_count   INT DEFAULT 0;

    -- -------------------------------------------------------------------------
    -- Validate input format
    -- -------------------------------------------------------------------------
    IF JSON_TYPE(p_cursor_positions) <> 'OBJECT' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cursor_positions_json must be a JSON object map';
    END IF;

    -- -------------------------------------------------------------------------
    -- Resolve subscription from consumer
    -- -------------------------------------------------------------------------
    SELECT subscription_id INTO v_subscription_id
      FROM consumers
     WHERE id = p_consumer_id;

    IF v_subscription_id IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'UNKNOWN_CONSUMER';
    END IF;

    -- -------------------------------------------------------------------------
    -- Expand JSON into MEMORY temp table for JOIN efficiency
    --
    -- Item #52 evaluation: temp table path retained (see file header).
    -- -------------------------------------------------------------------------
    DROP TEMPORARY TABLE IF EXISTS tmp_cursor_updates;
    CREATE TEMPORARY TABLE tmp_cursor_updates (
        cursor_id BIGINT PRIMARY KEY,
        position  BIGINT NOT NULL
    ) ENGINE = MEMORY;

    INSERT INTO tmp_cursor_updates (cursor_id, position)
    SELECT CAST(jt.cursor_key AS UNSIGNED),
           CAST(JSON_UNQUOTE(JSON_EXTRACT(p_cursor_positions,
                CONCAT('$."', jt.cursor_key, '"'))) AS UNSIGNED)
      FROM JSON_TABLE(JSON_KEYS(p_cursor_positions), '$[*]'
               COLUMNS(cursor_key VARCHAR(64) PATH '$')) AS jt;

    SELECT COUNT(*) INTO v_input_count FROM tmp_cursor_updates;

    IF v_input_count = 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'At least one cursor position must be provided';
    END IF;

    -- -------------------------------------------------------------------------
    -- Validate that every supplied position is a forward advance
    -- -------------------------------------------------------------------------
    SELECT COUNT(*) INTO v_updated_count
      FROM tmp_cursor_updates u
      JOIN cursors c ON c.id = u.cursor_id
                     AND c.subscription_id = v_subscription_id
     WHERE u.position > c.position;

    IF v_updated_count < v_input_count THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'STALE_COMMIT';
    END IF;

    -- -------------------------------------------------------------------------
    -- Advance cursor positions
    -- -------------------------------------------------------------------------
    UPDATE cursors c
      JOIN tmp_cursor_updates u ON u.cursor_id = c.id
       SET c.position = u.position
     WHERE c.subscription_id = v_subscription_id
       AND u.position > c.position;

    -- -------------------------------------------------------------------------
    -- Item #53: Release leases immediately on commit
    --
    -- NULL out locked_until for the committed cursors so other consumers can
    -- acquire these partitions without waiting for the lease to expire (~3s).
    -- Only releases leases held by p_consumer_id; does not touch leases held
    -- by other consumers (rare edge case: consumer crashed mid-processing and
    -- another consumer acquired the same cursor).
    -- -------------------------------------------------------------------------
    UPDATE consumer_leases cl
      JOIN tmp_cursor_updates u ON u.cursor_id = cl.cursor_id
       SET cl.locked_until = NULL
     WHERE cl.consumer_id = p_consumer_id;

    DROP TEMPORARY TABLE IF EXISTS tmp_cursor_updates;
END;
