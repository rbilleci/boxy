-- sp_events__publish_multi  (v2 — single-transaction bulk INSERT)
--
-- Replaces the v1 loop implementation (N procedure calls, N transactions) with a
-- set-based approach that resolves all partition IDs in one JOIN pass, then commits
-- both the events and unprocessed_events tables in a single transaction.
--
-- INPUT
--   p_events JSON  — array of event objects, each with the fields:
--       "path"  VARCHAR(4000)  namespace path (e.g. "tenant-a/payments")
--       "topic" VARCHAR(500)   topic name     (e.g. "orders")
--       "key"   VARCHAR(255)   routing key used for CRC32 partition selection
--       "data"  LONGBLOB       event payload  (typically a JSON string)
--
-- ALGORITHM
--   1. JSON_TABLE expands the input array into rows; FOR ORDINALITY captures the
--      original array position so that the two INSERTs stay in the same order.
--   2. A JOIN on namespaces (path_hash index) + topics (namespace_id, name, partitions
--      covering index) resolves the partition_id as:
--          (topic_id << 16) + (CRC32(key) % partitions)
--      This is the same formula used by fn_resolve_partition_id() and
--      sp_events__publish_advanced(), and avoids the MEMORY-table cache overhead.
--   3. The expanded rows are materialised into a temporary InnoDB table once.
--   4. A single START TRANSACTION / COMMIT wraps two bulk INSERTs:
--        a. INSERT INTO events (data) — generates N consecutive auto-increment IDs.
--        b. INSERT INTO unprocessed_events (id, partition_id) — correlates each row
--           to its event_id via:  LAST_INSERT_ID() + ROW_NUMBER() OVER (...) - 1
--           InnoDB guarantees that a single multi-row INSERT allocates contiguous IDs,
--           so this offset arithmetic is safe.
--
-- PERFORMANCE
--   v1 cost: N × (procedure call + transaction commit) ≈ O(N) round-trips
--   v2 cost: 1 × (procedure call + transaction commit) ≈ O(1) round-trips
--   Expected improvement: 10–50× for batch sizes > 10 events.
--
-- BENCHMARK GATE
--   Batch publish throughput must exceed N × single-publish throughput for N ≥ 10.
--   Results are recorded in docs/benchmarks.md.
DROP PROCEDURE IF EXISTS sp_events__publish_multi;
CREATE PROCEDURE sp_events__publish_multi(IN p_events JSON)
batch_pub: BEGIN
    DECLARE v_first_id BIGINT;
    DECLARE v_count    INT DEFAULT 0;

    -- ------------------------------------------------------------------
    -- Step 1: Materialise expanded rows + resolved partition IDs once.
    -- Using a temporary InnoDB table (MEMORY engine does not support
    -- BLOB/TEXT columns).  The table is session-scoped and dropped on exit.
    -- ------------------------------------------------------------------
    DROP TEMPORARY TABLE IF EXISTS _pub_batch;
    CREATE TEMPORARY TABLE _pub_batch (
        ord  INT      NOT NULL,
        data LONGBLOB NOT NULL,
        part BIGINT   NOT NULL
    ) ENGINE = InnoDB;

    INSERT INTO _pub_batch (ord, data, part)
    SELECT jt.ord,
           jt.data,
           (t.id << 16) + (CRC32(jt.k) % t.partitions)
    FROM JSON_TABLE(
             p_events, '$[*]'
             COLUMNS (
                 ord   FOR ORDINALITY,
                 path  VARCHAR(4000) PATH '$.path',
                 topic VARCHAR(500)  PATH '$.topic',
                 k     VARCHAR(255)  PATH '$.key',
                 data  LONGBLOB      PATH '$.data'
             )
         ) AS jt
    JOIN namespaces ns ON ns.path_hash = UNHEX(MD5(jt.path))
    JOIN topics     t  ON t.namespace_id = ns.id AND t.name = jt.topic;

    SET v_count = ROW_COUNT();
    IF v_count = 0 THEN
        DROP TEMPORARY TABLE IF EXISTS _pub_batch;
        LEAVE batch_pub;
    END IF;

    -- ------------------------------------------------------------------
    -- Step 2: Commit events + unprocessed_events in a single transaction.
    --
    -- LAST_INSERT_ID() after a multi-row INSERT returns the first allocated
    -- auto-increment ID.  InnoDB guarantees contiguous allocation within one
    -- INSERT statement, so offset arithmetic correctly maps each row:
    --   row at position R (1-based) → event_id = v_first_id + (R - 1)
    -- ------------------------------------------------------------------
    START TRANSACTION;

        INSERT INTO events (data)
        SELECT data FROM _pub_batch ORDER BY ord;

        SET v_first_id = LAST_INSERT_ID();

        INSERT INTO unprocessed_events (id, partition_id)
        SELECT v_first_id + ROW_NUMBER() OVER (ORDER BY ord) - 1,
               part
        FROM   _pub_batch
        ORDER  BY ord;

    COMMIT;

    DROP TEMPORARY TABLE IF EXISTS _pub_batch;
END;
