CREATE PROCEDURE sp_events__poll(
    IN p_subscription_id BIGINT,
    IN p_consumer_id     VARCHAR(36),
    IN p_batch_size      INT
)
BEGIN
    DECLARE v_start_key INT DEFAULT fn_random_int();
    DECLARE v_now       DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3);

    CREATE TEMPORARY TABLE IF NOT EXISTS temp_polled_events (
        cursor_id    BIGINT PRIMARY KEY,
        partition_id BIGINT NOT NULL,
        sequence     BIGINT NOT NULL,
        event_id     BIGINT NOT NULL
    ) ENGINE = MEMORY;
    DELETE FROM temp_polled_events;

    /* 1) Materialize the candidate rows up to the requested batch size */
    WITH candidate AS (
        SELECT
            c.id AS cursor_id,
            c.partition_id,
            s.sequence,
            s.event_id,
            ROW_NUMBER() OVER (
                ORDER BY (c.random_key >= v_start_key) DESC, c.random_key, c.id
            ) AS row_num
        FROM cursors c
        JOIN partitions p ON p.id = c.partition_id
        JOIN LATERAL (
            SELECT s.sequence, s.event_id
            FROM sequences s FORCE INDEX (idx_sequences__partition_sequence)
            WHERE s.partition_id = c.partition_id
              AND s.sequence > c.position
            ORDER BY s.sequence
            LIMIT 1
        ) s ON TRUE
        WHERE c.subscription_id = p_subscription_id
          AND (c.locked_until IS NULL OR c.locked_until < v_now OR c.locked_by = p_consumer_id)
          AND p.high_watermark > c.position
    )
    INSERT INTO temp_polled_events (cursor_id, partition_id, sequence, event_id)
    SELECT cursor_id, partition_id, sequence, event_id
    FROM candidate
    WHERE row_num <= p_batch_size;

    /* 2) Lock the selected cursors */
    UPDATE cursors c
    JOIN temp_polled_events sel ON sel.cursor_id = c.id
    SET c.locked_by    = p_consumer_id,
        c.locked_until = DATE_ADD(v_now, INTERVAL 3 SECOND)
    WHERE c.locked_until IS NULL OR c.locked_until < v_now OR c.locked_by = p_consumer_id;

    /* 3) Return the events for rows we actually hold now */
    SELECT
        sel.cursor_id,
        c.partition_id,
        sel.sequence,
        e.id   AS event_id,
        e.data
    FROM temp_polled_events sel
    JOIN cursors c
      ON c.id = sel.cursor_id
     AND c.locked_by = p_consumer_id
    JOIN events e
      ON e.id = sel.event_id;
END;
