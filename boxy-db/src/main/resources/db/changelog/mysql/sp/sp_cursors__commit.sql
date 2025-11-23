CREATE PROCEDURE sp_cursors__commit(
    IN p_session_id VARCHAR(36),
    IN p_cursor_positions JSON)
BEGIN
    DECLARE v_subscription_id BIGINT;
    DECLARE v_topic_filter JSON;
    DECLARE v_expected INT;
    DECLARE v_allowed INT;
    DECLARE v_stale INT;

    IF JSON_TYPE(p_cursor_positions) <> 'OBJECT' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cursor_positions_json must be a JSON object';
    END IF;

    SELECT subscription_id, topic_ids
      INTO v_subscription_id, v_topic_filter
      FROM sessions
     WHERE id = p_session_id;

    IF v_subscription_id IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'UNKNOWN_SESSION';
    END IF;

    WITH cursor_input AS (
        SELECT CAST(jk.key_name AS UNSIGNED) AS cursor_id,
               CAST(JSON_EXTRACT(p_cursor_positions, CONCAT('$."', jk.key_name, '"')) AS UNSIGNED) AS position
          FROM JSON_TABLE(JSON_KEYS(p_cursor_positions), '$[*]' COLUMNS(key_name VARCHAR(255) PATH '$')) jk
    ),
    allowed AS (
        SELECT ci.cursor_id, ci.position, c.position AS current_position
          FROM cursor_input ci
          JOIN cursors c ON c.id = ci.cursor_id AND c.subscription_id = v_subscription_id
          JOIN JSON_TABLE(v_topic_filter, '$[*]' COLUMNS(topic_id BIGINT PATH '$')) tf ON tf.topic_id = c.topic_id
    )
    SELECT
        (SELECT COUNT(*) FROM cursor_input),
        (SELECT COUNT(*) FROM allowed),
        (SELECT COUNT(*) FROM allowed WHERE position <= current_position)
      INTO v_expected, v_allowed, v_stale;

    IF v_expected = 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'At least one cursor must be provided';
    END IF;

    IF v_allowed < v_expected THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'INVALID_CURSOR';
    END IF;

    IF v_stale > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'STALE_COMMIT';
    END IF;

    UPDATE cursors c
    JOIN allowed a ON a.cursor_id = c.id
       SET c.position = a.position;
END;
