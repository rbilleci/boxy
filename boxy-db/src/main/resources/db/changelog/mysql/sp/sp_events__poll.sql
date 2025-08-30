CREATE PROCEDURE sp_events__poll(
    IN p_subscription_id BIGINT,
    IN p_consumer_id VARCHAR(36),
    IN p_batch_size INT
)
BEGIN
    DECLARE v_start_key INT DEFAULT fn_random_int();
    DECLARE v_now DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3);

    CREATE TEMPORARY TABLE IF NOT EXISTS temp_selected_sequences (
        cursor_id BIGINT PRIMARY KEY,
        partition_id BIGINT NOT NULL,
        sequence BIGINT NOT NULL,
        event_ids JSON NOT NULL
    ) ENGINE = MEMORY;
    DELETE FROM temp_selected_sequences;

    INSERT INTO temp_selected_sequences(cursor_id, partition_id, sequence, event_ids)
    SELECT t.cursor_id, t.partition_id, t.sequence, t.event_ids
      FROM (
            SELECT c.id AS cursor_id,
                   c.partition_id,
                   s.sequence,
                   s.event_ids,
                   SUM(JSON_LENGTH(s.event_ids)) OVER (
                       ORDER BY (c.random_key >= v_start_key) DESC, c.random_key
                       ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
                   ) AS cum_events
              FROM cursors c
              JOIN partitions p ON p.id = c.partition_id
              JOIN LATERAL (
                    SELECT s.sequence, s.event_ids
                      FROM sequences s
                     WHERE s.partition_id = c.partition_id
                       AND s.sequence > c.position
                     ORDER BY s.sequence
                     LIMIT 1
                ) s ON TRUE
             WHERE c.subscription_id = p_subscription_id
               AND (c.locked_until IS NULL OR c.locked_until < v_now OR c.locked_by_consumer_id = p_consumer_id)
               AND p.high_watermark > c.position
       ) t
     WHERE t.cum_events <= p_batch_size;

    UPDATE cursors c
       JOIN temp_selected_sequences tss ON c.id = tss.cursor_id
       SET c.locked_by_consumer_id = p_consumer_id,
           c.locked_until = DATE_ADD(v_now, INTERVAL 3 SECOND)
     WHERE c.locked_until IS NULL OR c.locked_until < v_now OR c.locked_by_consumer_id = p_consumer_id;

    DELETE tss FROM temp_selected_sequences tss
      JOIN cursors c ON c.id = tss.cursor_id
     WHERE c.locked_by_consumer_id <> p_consumer_id;

    SELECT tss.cursor_id,
           tss.partition_id,
           tss.sequence,
           e.id AS event_id,
           e.data
      FROM temp_selected_sequences tss
      JOIN JSON_TABLE(tss.event_ids, '$[*]' COLUMNS (event_id BIGINT PATH '$')) jt
      JOIN events e ON e.id = jt.event_id;
END;
