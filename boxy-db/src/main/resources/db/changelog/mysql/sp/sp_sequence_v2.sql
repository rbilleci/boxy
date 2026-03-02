-- sp_sequence  (v2 — optimised high_watermark UPDATE, added processed count)
--
-- Processes one batch of unprocessed events through the sequencer pipeline:
--   1. Claim a batch from unprocessed_events into temp_claimed_ids (pre-transaction,
--      READ COMMITTED so the SELECT does not hold long-lived locks).
--   2. INSERT claimed rows into sequences, assigning monotonically increasing
--      sequence numbers (AUTO_INCREMENT).
--   3. DELETE claimed rows from unprocessed_events using the STRAIGHT_JOIN hint
--      that was explicitly tuned to force the correct join order (temp table driving).
--   4. UPDATE partitions.high_watermark for each affected partition.
--
-- CHANGES FROM v1
--   - OUT parameter renamed: p_has_events → p_processed_count (returns the actual
--     count so callers can implement dynamic batch sizing).
--   - high_watermark UPDATE no longer joins back into the sequences table.
--     v1 used:  JOIN sequences s ON s.event_id = t.id  (no index on event_id,
--               forces a range scan of sequences which grows unboundedly)
--     v2 uses:  @first_seq + MAX(ROW_NUMBER() OVER (...)) - 1 arithmetic
--               InnoDB guarantees that a single multi-row INSERT allocates
--               contiguous auto-increment values in insertion order.
--               After  INSERT INTO sequences ... ORDER BY partition_id, id
--               the sequence for row at position R (1-based) is @first_seq + R - 1.
--               The max sequence for partition P = @first_seq + (last row of P) - 1,
--               which equals @first_seq + MAX(global_row_number_for_P) - 1.
--               This eliminates the sequences table re-read entirely.
--   - Inline documentation added throughout.
--
-- BENCHMARK GATE (item #38)
--   Sequencer throughput (BenchmarkIT.sequencer_throughput) must not decrease
--   relative to v1 baseline recorded in docs/benchmarks.md.
DROP PROCEDURE IF EXISTS sp_sequence;
CREATE PROCEDURE sp_sequence(IN p_batch_size INT, OUT p_processed_count INT)
BEGIN
    -- -----------------------------------------------------------------------
    -- Step 1: Claim a batch.
    --
    -- Runs OUTSIDE the transaction so that the SELECT does not hold shared
    -- locks for the duration of the batch — INSERT + DELETE + UPDATE below
    -- can proceed at READ COMMITTED isolation without starving publishers.
    -- -----------------------------------------------------------------------
    DELETE FROM temp_claimed_ids;

    INSERT INTO temp_claimed_ids (id, partition_id)
        SELECT id, partition_id
        FROM   unprocessed_events
        ORDER  BY id
        LIMIT  p_batch_size;

    -- -----------------------------------------------------------------------
    -- Step 2: Begin the sequencing transaction at READ COMMITTED.
    -- -----------------------------------------------------------------------
    SET TRANSACTION ISOLATION LEVEL READ COMMITTED;
    START TRANSACTION;

        -- -------------------------------------------------------------------
        -- Step 3: Assign sequence numbers.
        --
        -- ORDER BY partition_id, id ensures that events within each partition
        -- receive contiguous, ascending sequence values.  This makes
        -- partition-scoped cursor reads efficient (range scans on
        -- idx_sequences__partition_sequence).
        -- -------------------------------------------------------------------
        INSERT INTO sequences (partition_id, event_id)
            SELECT partition_id, id
            FROM   temp_claimed_ids
            ORDER  BY partition_id, id;

        SET p_processed_count = ROW_COUNT();

        -- -------------------------------------------------------------------
        -- Step 4: Remove sequenced events from the unprocessed table.
        --
        -- STRAIGHT_JOIN forces temp_claimed_ids as the outer (driving) table.
        -- Without the hint, MySQL might flip the join order and perform a full
        -- scan of unprocessed_events — benchmarks showed a 3–5× regression
        -- without this hint.
        -- -------------------------------------------------------------------
        DELETE ue
        FROM   temp_claimed_ids b
               STRAIGHT_JOIN unprocessed_events ue ON b.id = ue.id;

        -- -------------------------------------------------------------------
        -- Step 5: Advance high_watermarks for affected partitions.
        --
        -- Instead of re-joining the sequences table by event_id (which lacks
        -- an index and degrades as sequences grows), we compute the maximum
        -- sequence per partition using the known AUTO_INCREMENT offset:
        --
        --   @_first_seq = sequence assigned to the FIRST row of this batch.
        --   For the K-th row of the batch (ORDER BY partition_id, id),
        --     sequence = @_first_seq + (K - 1).
        --
        --   Max sequence for partition P
        --     = @_first_seq + MAX(global_row_number_among_P's_rows) - 1.
        --
        -- ROW_NUMBER() OVER (ORDER BY partition_id, id) matches the INSERT
        -- order exactly, so the arithmetic is correct.
        -- -------------------------------------------------------------------
        IF p_processed_count > 0 THEN
            SET @_first_seq = LAST_INSERT_ID();

            UPDATE partitions p
            JOIN (
                SELECT partition_id,
                       @_first_seq + MAX(rn) - 1 AS max_seq
                FROM (
                    SELECT partition_id,
                           ROW_NUMBER() OVER (ORDER BY partition_id, id) AS rn
                    FROM   temp_claimed_ids
                ) ranked
                GROUP BY partition_id
            ) seqs ON p.id = seqs.partition_id
            SET p.high_watermark = seqs.max_seq;
        END IF;

    COMMIT;
END;
