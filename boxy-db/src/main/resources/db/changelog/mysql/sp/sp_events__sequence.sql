CREATE PROCEDURE sp_events__sequence(IN p_batch_size INT)
BEGIN
    DECLARE v_sequenced_events INT DEFAULT 0;
    CREATE TEMPORARY TABLE IF NOT EXISTS temp_claimed_ids (
        id BIGINT PRIMARY KEY,
        partition_id BIGINT NOT NULL
    ) ENGINE = MEMORY;
    TRUNCATE TABLE temp_claimed_ids;

    START TRANSACTION;

        INSERT INTO temp_claimed_ids (id, partition_id)
            SELECT id, partition_id FROM (
                SELECT id,
                       partition_id,
                       ROW_NUMBER() OVER (PARTITION BY partition_id ORDER BY id) AS rn
                FROM unprocessed_events
            ) e
            WHERE e.rn <= p_batch_size;

        INSERT INTO sequences (partition_id, event_ids)
            SELECT partition_id,
                   JSON_ARRAYAGG(id ORDER BY id) AS event_ids
            FROM temp_claimed_ids
            GROUP BY partition_id
            ORDER BY partition_id;

        SELECT COUNT(*) INTO v_sequenced_events FROM temp_claimed_ids;

        -- CAREFUL!!:
        -- Changing this join can impact the performance of the sequencer.
        DELETE ue FROM temp_claimed_ids b
            STRAIGHT_JOIN unprocessed_events ue ON b.id = ue.id;

        UPDATE partitions p
        JOIN (
            SELECT s.partition_id, MAX(s.sequence) AS max_sequence
            FROM sequences s
            JOIN (SELECT DISTINCT partition_id FROM temp_claimed_ids) t
                ON s.partition_id = t.partition_id
            GROUP BY s.partition_id
        ) seqs ON p.id = seqs.partition_id
        SET p.high_watermark = seqs.max_sequence;

    COMMIT;
    SELECT v_sequenced_events AS sequenced_events;
END;
