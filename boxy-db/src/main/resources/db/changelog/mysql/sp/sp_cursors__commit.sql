CREATE PROCEDURE sp_cursors__commit(
    IN p_session_id VARCHAR(36),
    IN p_cursor_positions JSON)
BEGIN
    DECLARE v_subscription_id BIGINT;
    DECLARE v_input_count INT DEFAULT 0;
    DECLARE v_updated_count INT DEFAULT 0;

    IF JSON_TYPE(p_cursor_positions) <> 'OBJECT' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cursor_positions_json must be a JSON object map';
    END IF;

    SELECT subscription_id INTO v_subscription_id FROM consumers WHERE id = p_session_id;

    IF v_subscription_id IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'UNKNOWN_SESSION';
    END IF;

    DROP TEMPORARY TABLE IF EXISTS tmp_cursor_updates;
    CREATE TEMPORARY TABLE tmp_cursor_updates (
        cursor_id BIGINT PRIMARY KEY,
        position  BIGINT NOT NULL
    ) ENGINE = MEMORY;

    INSERT INTO tmp_cursor_updates (cursor_id, position)
    SELECT CAST(jt.cursor_key AS UNSIGNED),
           CAST(JSON_UNQUOTE(JSON_EXTRACT(p_cursor_positions, CONCAT('$.', jt.cursor_key))) AS UNSIGNED)
      FROM JSON_TABLE(JSON_KEYS(p_cursor_positions), '$[*]' COLUMNS(cursor_key VARCHAR(64) PATH '$')) AS jt;

    SELECT COUNT(*) INTO v_input_count FROM tmp_cursor_updates;

    IF v_input_count = 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'At least one cursor position must be provided';
    END IF;

    SELECT COUNT(*) INTO v_updated_count
      FROM tmp_cursor_updates u
      JOIN cursors c ON c.id = u.cursor_id AND c.subscription_id = v_subscription_id
     WHERE u.position > c.position;

    IF v_updated_count < v_input_count THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'STALE_COMMIT';
    END IF;

    UPDATE cursors c
      JOIN tmp_cursor_updates u ON u.cursor_id = c.id
       SET c.position = u.position
     WHERE c.subscription_id = v_subscription_id
       AND u.position > c.position;

    DROP TEMPORARY TABLE IF EXISTS tmp_cursor_updates;
END;
