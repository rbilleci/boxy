CREATE PROCEDURE sp_events__sequence(IN p_batch_size INT)
BEGIN
    DECLARE v_has_events INT DEFAULT 0;

    CREATE TEMPORARY TABLE IF NOT EXISTS temp_claimed_ids (
        id BIGINT PRIMARY KEY,
        partition_id BIGINT NOT NULL
        ) ENGINE = MEMORY;
    DELETE FROM temp_claimed_ids;

    START TRANSACTION;

        -- COPY EVENTS INTO TEMPORARY TABLE
        INSERT INTO temp_claimed_ids (id, partition_id)
            SELECT id, partition_id FROM (
                SELECT id,
                       partition_id,
                       ROW_NUMBER() OVER (PARTITION BY partition_id ORDER BY id) AS rn
                FROM unprocessed_events
            ) e
            WHERE e.rn <= p_batch_size;

        -- INSERT THE SEQUENCE BATCHES
        -- WE CAN'T USE JSON_ARRAYAGG(id ORDER BY id) ON MYSQL 8.0.x
        -- WE CAN OPTIMIZE THIS TO USE JSON_ARRAYAGG WHEN WE MOVE TO A NEWER VERSION AS THE MINIMUM
        INSERT INTO sequences (partition_id, event_ids)
             SELECT partition_id,
                    CAST(CONCAT('[', GROUP_CONCAT(id ORDER BY id SEPARATOR ','), ']') AS JSON) AS event_ids
               FROM temp_claimed_ids
              GROUP BY partition_id;

        -- DETERMINE IF THERE WERE ANY SEQUENCED EVENTS
        SET v_has_events = IF(ROW_COUNT() > 0, 1, 0);

        -- CAREFUL!!:
        -- Changing this join can impact the performance of the sequencer.
        DELETE ue FROM temp_claimed_ids b
            STRAIGHT_JOIN unprocessed_events ue ON b.id = ue.id;

        -- UPDATE HIGH WATERMARKS
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

    SELECT v_has_events AS has_events;
END;
