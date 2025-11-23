CREATE PROCEDURE sp_events__poll(
    IN p_consumer_id VARCHAR(36)
)
BEGIN
    DECLARE v_start_key INT DEFAULT fn_random_int();
    DECLARE v_now       DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3);
    DECLARE v_subscription_id BIGINT;
    DECLARE v_topic_ids JSON;
    DECLARE v_batch_size INT DEFAULT 100;

    SELECT subscription_id, topic_ids INTO v_subscription_id, v_topic_ids
      FROM consumers
     WHERE id = p_consumer_id;

    IF v_subscription_id IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'UNKNOWN_CONSUMER';
    END IF;

    UPDATE consumers
       SET heartbeat_detected_at = v_now,
           heartbeat_deadline = DATE_ADD(v_now, INTERVAL heartbeat_interval SECOND)
     WHERE id = p_consumer_id;

    DROP TEMPORARY TABLE IF EXISTS tmp_selected_sequences;
    CREATE TEMPORARY TABLE tmp_selected_sequences (
        cursor_id    BIGINT,
        partition_id BIGINT,
        sequence     BIGINT,
        event_id     BIGINT,
        PRIMARY KEY (cursor_id, sequence)
    ) ENGINE = MEMORY;

    /* 1) Capture the rows we plan to lock */
    INSERT INTO tmp_selected_sequences (cursor_id, partition_id, sequence, event_id)
    WITH candidate AS (
        SELECT
            c.id AS cursor_id,
            c.partition_id,
            s.sequence,
            s.event_id,
            ROW_NUMBER() OVER (
                ORDER BY (c.random_key >= v_start_key) DESC, c.random_key, c.id, s.sequence
            ) AS row_num
        FROM cursors c
        JOIN partitions p ON p.id = c.partition_id
        LEFT JOIN leases l ON l.cursor_id = c.id
        JOIN LATERAL (
            SELECT s.sequence, s.event_id
            FROM sequences s FORCE INDEX (idx_sequences__partition_sequence)
            WHERE s.partition_id = c.partition_id
              AND s.sequence > IF(
                  l.consumer_id = p_consumer_id,
                  COALESCE(l.last_read_position, c.position),
                  c.position)
            ORDER BY s.sequence
        ) s ON TRUE
        WHERE c.subscription_id = v_subscription_id
          AND JSON_CONTAINS(v_topic_ids, CAST(c.topic_id AS JSON), '$')
          AND (
              l.cursor_id IS NULL OR
              l.locked_until IS NULL OR
              l.locked_until < v_now OR
              l.consumer_id = p_consumer_id
          )
          AND p.high_watermark > c.position
    )
    SELECT cursor_id, partition_id, sequence, event_id
    FROM candidate
    WHERE row_num <= v_batch_size;

    /* 2) Lock the selected cursors */
    INSERT INTO leases (cursor_id)
    SELECT c.id
      FROM cursors c
      JOIN tmp_selected_sequences sel ON sel.cursor_id = c.id
    ON DUPLICATE KEY UPDATE
        cursor_id = leases.cursor_id;

    UPDATE leases l
    JOIN tmp_selected_sequences sel ON sel.cursor_id = l.cursor_id
    JOIN cursors c ON c.id = l.cursor_id
    SET l.consumer_id  = p_consumer_id,
        l.locked_until = DATE_ADD(v_now, INTERVAL 3 SECOND),
        l.last_read_position = CASE
            WHEN l.consumer_id = p_consumer_id THEN COALESCE(l.last_read_position, c.position)
            ELSE c.position
        END
    WHERE l.locked_until IS NULL OR l.locked_until < v_now OR l.consumer_id = p_consumer_id;

    UPDATE leases l
    JOIN (
        SELECT cursor_id, MAX(sequence) AS last_sequence
          FROM tmp_selected_sequences
         GROUP BY cursor_id
    ) sel ON sel.cursor_id = l.cursor_id
    SET l.last_read_position = GREATEST(IFNULL(l.last_read_position, 0), sel.last_sequence)
    WHERE l.consumer_id = p_consumer_id;

    /* 3) Return the events for rows we actually hold now */
    SELECT
        sel.cursor_id,
        c.partition_id,
        sel.sequence,
        e.id   AS event_id,
        e.data
    FROM tmp_selected_sequences sel
    JOIN cursors c ON c.id = sel.cursor_id
    JOIN leases l
      ON l.cursor_id = sel.cursor_id
     AND l.consumer_id = p_consumer_id
    JOIN events e
      ON e.id = sel.event_id;

    /* 4) Return polling metadata */
    SELECT 0.001 AS polling_probability;

    DROP TEMPORARY TABLE IF EXISTS tmp_selected_sequences;
END;
