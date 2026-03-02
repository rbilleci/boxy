-- sp_consumers__gc  (v2 — batched deletion with explicit lease cleanup)
--
-- === Item #58: Benchmark consumer_gc under load ===
--   With 1000+ consumers, the original single-transaction DELETE locked all
--   expired consumer rows at once, stalling concurrent polls for the lock
--   hold duration.
--
--   Benchmark gate: Run PipelineBenchmarkIT.pipeline_fourProducersFourConsumers()
--   while injecting 1000 expired consumers and firing consumer_gc concurrently.
--   Poll p99 latency must not increase by more than 20% vs. the baseline run
--   without concurrent GC.  See: docs/benchmarks.md §Consumer GC Evaluation.
--
-- === Item #59: Batch consumer deletion with LIMIT ===
--   The original procedure deleted all expired consumers in a single transaction.
--   For large deployments (1000+ consumers), this held row locks on every expired
--   consumer row for the full DELETE duration, blocking concurrent polls that need
--   to look up consumers by subscription_id or heartbeat_deadline.
--
--   Change: loop in batches of v_batch_size (default 100) until all expired
--   consumers are removed.  Each batch is its own transaction, bounding the
--   lock hold time per iteration.
--
-- === Item #60: Explicit lease cleanup before consumer deletion ===
--   consumer_leases has ON DELETE CASCADE from consumers.id.  With many leases
--   per consumer (e.g. one per partition in the subscription), the CASCADE acquires
--   row locks on ALL lease rows for the deleted consumer in one step.  This can
--   hold locks for tens of milliseconds if a consumer held 100+ partitions.
--
--   Change: in Step 1, explicitly DELETE from consumer_leases WHERE consumer_id
--   IN (expired consumers) in batches of v_batch_size.  Each batch is committed
--   independently, so the maximum lock hold per step is bounded.
--
--   After Step 1 completes, consumer_leases for all expired consumers are already
--   gone; the Step 2 consumer DELETE triggers no CASCADE work.

DROP PROCEDURE IF EXISTS sp_consumers__gc;
CREATE PROCEDURE sp_consumers__gc()
BEGIN
    DECLARE v_timestamp  TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3);
    DECLARE v_batch_size INT DEFAULT 100;
    DECLARE v_deleted    INT DEFAULT 0;

    -- -------------------------------------------------------------------------
    -- Step 1: Item #60 — explicit batch lease cleanup (avoids large CASCADE)
    --
    -- Remove consumer_leases rows for all expired consumers in v_batch_size
    -- chunks.  Each iteration is its own transaction to bound lock hold time.
    -- The JOIN filters only leases belonging to consumers that are about to
    -- be GC'd, leaving active consumers' leases untouched.
    -- -------------------------------------------------------------------------
    REPEAT
        START TRANSACTION;

        DELETE cl
          FROM consumer_leases cl
          JOIN consumers c ON c.id = cl.consumer_id
         WHERE c.heartbeat_deadline <= v_timestamp
         LIMIT v_batch_size;

        SET v_deleted = ROW_COUNT();
        COMMIT;
    UNTIL v_deleted = 0 END REPEAT;

    -- -------------------------------------------------------------------------
    -- Step 2: Item #59 — batch consumer deletion with LIMIT
    --
    -- After Step 1, the consumer_leases table has no rows for expired consumers;
    -- the CASCADE triggered by each DELETE here does no work.  Batching keeps
    -- each transaction small and bounds row-lock hold time.
    -- -------------------------------------------------------------------------
    REPEAT
        START TRANSACTION;

        DELETE FROM consumers
         WHERE heartbeat_deadline <= v_timestamp
         LIMIT v_batch_size;

        SET v_deleted = ROW_COUNT();
        COMMIT;
    UNTIL v_deleted = 0 END REPEAT;
END;
