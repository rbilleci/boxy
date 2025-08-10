CREATE PROCEDURE sp_events__sequence(
    IN p_topic_id BIGINT,
    IN p_batch_size INT)
BEGIN
    DECLARE v_next_sequence BIGINT;

    -- Determine starting sequence for this topic
    SELECT IFNULL(MAX(e.sequence), 0)
      INTO v_next_sequence
      FROM events e
      JOIN partitions p ON e.partition_id = p.id
     WHERE p.topic_id = p_topic_id;

    -- Collect events to update
    CREATE TEMPORARY TABLE tmp_events (
        event_id BIGINT,
        partition_id BIGINT
    ) ENGINE=MEMORY;

    INSERT INTO tmp_events (event_id, partition_id)
    SELECT e.id, e.partition_id
      FROM events e
      JOIN partitions p ON e.partition_id = p.id
     WHERE p.topic_id = p_topic_id
       AND e.sequence IS NULL
     ORDER BY e.id ASC
     LIMIT p_batch_size
     FOR UPDATE;

    -- Assign sequence numbers in id order
    SET @seq := v_next_sequence;
    UPDATE events e
    JOIN (
        SELECT event_id
          FROM tmp_events
         ORDER BY event_id
    ) t ON e.id = t.event_id
    SET e.sequence = (@seq := @seq + 1);

    -- Update high watermarks for affected partitions
    UPDATE partitions p
    JOIN (
        SELECT e.partition_id, MAX(e.sequence) AS max_seq
          FROM events e
          JOIN tmp_events t ON e.id = t.event_id
         GROUP BY e.partition_id
    ) s ON p.id = s.partition_id
    SET p.high_watermark = GREATEST(p.high_watermark, s.max_seq);

    DROP TEMPORARY TABLE tmp_events;
END;
