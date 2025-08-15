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
            SELECT id, partition_id 
            FROM unprocessed_events 
            ORDER BY ID LIMIT p_batch_size;

        INSERT INTO sequences (event_id, partition_id) 
            SELECT id, partition_id FROM temp_claimed_ids 
            ORDER BY id;

        SELECT ROW_COUNT() INTO v_sequenced_events;

        -- CAREFUL!!: 
        -- Changing this join can impact the performance of the sequencer.
        DELETE ue FROM temp_claimed_ids b 
            STRAIGHT_JOIN unprocessed_events ue ON b.id = ue.id;

        UPDATE partitions p
        JOIN (
            SELECT s.partition_id, MAX(s.sequence) AS max_sequence
            FROM sequences s
            JOIN temp_claimed_ids t ON s.event_id = t.id
            GROUP BY s.partition_id
        ) seqs ON p.id = seqs.partition_id
        SET p.high_watermark = GREATEST(p.high_watermark, seqs.max_sequence);

    COMMIT;
    SELECT v_sequenced_events AS sequenced_events;
END;
