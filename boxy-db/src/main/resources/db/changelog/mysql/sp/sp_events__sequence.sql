CREATE PROCEDURE sp_events__sequence(IN p_batch_size INT, OUT p_has_events INT)
BEGIN
    DECLARE v_has_events INT DEFAULT 0;

    DELETE FROM temp_claimed_ids;

    -- COPY EVENTS INTO TEMPORARY TABLE
    INSERT INTO temp_claimed_ids (id, partition_id)
        SELECT id, partition_id
        FROM unprocessed_events
        ORDER BY id
        LIMIT p_batch_size;

    -- FOR PERFORMANCE: START THE TRANSACTION AFTER COPYING INTO THE TEMP TABLE
    START TRANSACTION;

        -- INSERT INDIVIDUAL SEQUENCES
        INSERT INTO sequences (partition_id, event_id)
            SELECT partition_id, id
            FROM temp_claimed_ids
            ORDER BY partition_id, id;

        -- DETERMINE IF THERE WERE ANY SEQUENCED EVENTS
        SET v_has_events = IF(ROW_COUNT() > 0, 1, 0);

        -- CAREFUL!!:
        -- Changing this join can impact the performance of the sequencer.
        DELETE ue FROM temp_claimed_ids b
            STRAIGHT_JOIN unprocessed_events ue ON b.id = ue.id;

        -- UPDATE HIGH WATERMARKS ONCE PER PARTITION USING JUST-INSERTED ROWS
        UPDATE partitions p
        JOIN (
            SELECT s.partition_id, MAX(s.sequence) AS max_sequence
              FROM sequences s
              JOIN temp_claimed_ids t ON s.event_id = t.id
             GROUP BY s.partition_id
        ) seqs ON p.id = seqs.partition_id
        SET p.high_watermark = seqs.max_sequence;

    COMMIT;

    SET p_has_events = v_has_events;
END;
