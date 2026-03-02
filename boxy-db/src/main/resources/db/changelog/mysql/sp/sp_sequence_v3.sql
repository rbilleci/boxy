-- sp_sequence (v3 — EXIT HANDLER with ROLLBACK + temp table cleanup, Section 2 item 74)
-- v2: replaced sequences table re-scan with ROW_NUMBER watermark (changeset 3)
-- v3: adds EXIT HANDLER with ROLLBACK for error recovery
DROP PROCEDURE IF EXISTS sp_sequence;
CREATE PROCEDURE sp_sequence(IN p_batch_size INT, OUT p_processed_count INT)
BEGIN
    DECLARE v_batch_limit INT;

    -- EXIT HANDLER: rolls back the sequencing transaction on any SQL error and
    -- cleans up the temp table.  Without this, a partial batch could sequence
    -- events without removing them from unprocessed_events.
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        DROP TEMPORARY TABLE IF EXISTS temp_claimed_ids;
        SET p_processed_count = 0;
        RESIGNAL;
    END;

    SET v_batch_limit = IF(p_batch_size IS NULL OR p_batch_size <= 0, 1000,
                           LEAST(p_batch_size, 10000));
    SET p_processed_count = 0;

    INSERT INTO temp_claimed_ids (id, partition_id)
    SELECT id, partition_id
      FROM unprocessed_events
     ORDER BY partition_id, id
     LIMIT v_batch_limit;

    SET p_processed_count = ROW_COUNT();

    IF p_processed_count > 0 THEN

        START TRANSACTION;

            INSERT INTO sequences (partition_id, event_id)
            SELECT partition_id, id
              FROM temp_claimed_ids
             ORDER BY partition_id, id;

            SET @_first_seq = LAST_INSERT_ID();

            DELETE u
              FROM unprocessed_events u
        STRAIGHT_JOIN temp_claimed_ids t ON t.id = u.id;

            UPDATE partitions p
              JOIN (
                  SELECT partition_id,
                         @_first_seq + MAX(rn) - 1 AS max_seq
                    FROM (
                        SELECT partition_id,
                               ROW_NUMBER() OVER (ORDER BY partition_id, id) AS rn
                          FROM temp_claimed_ids
                    ) ranked
                   GROUP BY partition_id
              ) seqs ON p.id = seqs.partition_id
               SET p.high_watermark = seqs.max_seq;

        COMMIT;

    END IF;
END;
