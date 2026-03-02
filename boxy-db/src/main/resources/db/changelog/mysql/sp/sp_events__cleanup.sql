-- sp_events__cleanup: batch event retention cleanup.
--
-- Item #97: Provide example retention/cleanup stored procedure.
--
-- Deletes events older than p_retain_days days in batches of p_batch_size rows.
-- Each batch is its own transaction to bound lock hold time (same pattern as
-- sp_consumers__gc_v3).
--
-- PREREQUISITES:
--   - events.created_at column (changeset 12, items #95-#96)
--   - events.id is a primary key — the inner SELECT uses a covering scan on
--     idx_events__created_at and only acquires row locks in the batch DELETE.
--
-- SAFE TO RUN CONCURRENTLY: concurrent DELETE on non-overlapping PK ranges
-- does not block ongoing INSERT/SELECT to the events table.
--
-- EXAMPLE:
--   -- Delete events older than 30 days, 1000 rows per transaction:
--   CALL sp_events__cleanup(30, 1000);
--
-- The procedure is intentionally not wired to the MySQL event scheduler by
-- default.  Add your own event with the retention window that matches your SLA:
--   CREATE EVENT events_cleanup
--       ON SCHEDULE EVERY 1 HOUR
--       DO CALL sp_events__cleanup(30, 1000);
CREATE PROCEDURE sp_events__cleanup(
    IN p_retain_days INT,
    IN p_batch_size  INT
)
BEGIN
    DECLARE v_cutoff    TIMESTAMP(3);
    DECLARE v_batch     INT;
    DECLARE v_deleted   INT DEFAULT 0;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    -- Apply sensible bounds
    SET v_cutoff    = DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL p_retain_days DAY);
    SET v_batch     = IF(p_batch_size IS NULL OR p_batch_size <= 0, 1000,
                         LEAST(p_batch_size, 10000));

    REPEAT
        START TRANSACTION;

        DELETE e
          FROM events e
         WHERE e.created_at < v_cutoff
         LIMIT v_batch;

        SET v_deleted = ROW_COUNT();
        COMMIT;
    UNTIL v_deleted = 0 END REPEAT;
END;
