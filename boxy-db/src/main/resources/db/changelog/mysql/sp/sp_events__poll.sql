CREATE PROCEDURE sp_events__poll(
    IN p_subscription_id BIGINT,
    IN p_consumer_id     VARCHAR(36),
    IN p_batch_size      INT
)
BEGIN
    DECLARE v_start_key INT DEFAULT fn_random_int();
    DECLARE v_now       DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3);
    DECLARE v_sel       JSON;  -- holds the selected rows once

    /* 1) Build the selected set once into v_sel (no result set emitted) */
    WITH candidate AS (
        SELECT
            c.id AS cursor_id,
            c.partition_id,
            s.sequence,
            s.event_ids,
            JSON_LENGTH(s.event_ids) AS event_count,
            SUM(JSON_LENGTH(s.event_ids)) OVER (
                ORDER BY (c.random_key >= v_start_key) DESC, c.random_key, c.id
                ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
            ) AS cumulative_events
        FROM cursors c
        JOIN partitions p ON p.id = c.partition_id
        JOIN LATERAL (
            SELECT s.sequence, s.event_ids
            FROM sequences s FORCE INDEX (idx_sequences__partition_sequence)
            WHERE s.partition_id = c.partition_id
              AND s.sequence > c.position
            ORDER BY s.sequence
            LIMIT 1
        ) s ON TRUE
        WHERE c.subscription_id = p_subscription_id
          AND (c.locked_until IS NULL OR c.locked_until < v_now OR c.locked_by = p_consumer_id)
          AND p.high_watermark > c.position
    ),
    selected AS (
        SELECT cursor_id, partition_id, sequence, event_ids
        FROM candidate
        WHERE cumulative_events - event_count < p_batch_size
    )
    SELECT
        COALESCE(
            JSON_ARRAYAGG(
                JSON_OBJECT(
                    'cursor_id',    cursor_id,
                    'partition_id', partition_id,
                    'sequence',     sequence,
                    'event_ids',    event_ids
                )
            ),
            JSON_ARRAY()
        )
    INTO v_sel
    FROM selected;

    /* 2) Lock the selected cursors */
    UPDATE cursors c
    JOIN JSON_TABLE(v_sel, '$[*]'
        COLUMNS (cursor_id BIGINT PATH '$.cursor_id')
    ) sel ON sel.cursor_id = c.id
    SET c.locked_by    = p_consumer_id,
        c.locked_until = DATE_ADD(v_now, INTERVAL 3 SECOND)
    WHERE c.locked_until IS NULL OR c.locked_until < v_now OR c.locked_by = p_consumer_id;

    /* 3) Return the events for rows we actually hold now */
    SELECT
        jt.cursor_id,
        c.partition_id,
        jt.sequence,
        e.id   AS event_id,
        e.data
    FROM JSON_TABLE(v_sel, '$[*]'
        COLUMNS (
            cursor_id    BIGINT PATH '$.cursor_id',
            partition_id BIGINT PATH '$.partition_id',
            sequence     BIGINT PATH '$.sequence',
            NESTED PATH '$.event_ids[*]'
                COLUMNS (event_id BIGINT PATH '$')
        )
    ) jt
    JOIN cursors c
      ON c.id = jt.cursor_id
     AND c.locked_by = p_consumer_id
    JOIN events e
      ON e.id = jt.event_id;
END;
