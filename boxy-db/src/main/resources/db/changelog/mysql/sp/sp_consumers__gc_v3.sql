-- sp_consumers__gc  (v3 — EXIT HANDLER for SQLEXCEPTION)
--
-- === Item #75: Stored procedure error handling ===
--   Adds DECLARE EXIT HANDLER FOR SQLEXCEPTION.
--   On error during a batch transaction: ROLLBACK the in-progress batch
--   then RESIGNAL so the caller (MySQL event scheduler) sees the error.
--
--   All other behaviour is identical to v2 (batched deletion with explicit
--   lease cleanup).
DROP PROCEDURE IF EXISTS sp_consumers__gc;
CREATE PROCEDURE sp_consumers__gc()
BEGIN
    DECLARE v_timestamp  TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3);
    DECLARE v_batch_size INT DEFAULT 100;
    DECLARE v_deleted    INT DEFAULT 0;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

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
