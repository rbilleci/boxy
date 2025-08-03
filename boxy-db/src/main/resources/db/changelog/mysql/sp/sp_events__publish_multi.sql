CREATE PROCEDURE sp_events__publish_multi(IN p_events JSON)
BEGIN
    DECLARE v_idx INT DEFAULT 0;
    DECLARE v_len INT;
    DECLARE v_path VARCHAR(4000);
    DECLARE v_topic  VARCHAR(500);
    DECLARE v_key  VARCHAR(255);
    DECLARE v_data   JSON;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN RESIGNAL; END;

    -- N ELEMENTS
    SET v_len = JSON_LENGTH(p_events);

    -- LOOP
    WHILE v_idx < v_len DO
        SET v_path = JSON_UNQUOTE(JSON_EXTRACT(p_events, CONCAT('$[', v_idx, '].path')));
        SET v_topic  = JSON_UNQUOTE(JSON_EXTRACT(p_events, CONCAT('$[', v_idx, '].topic')));
        SET v_key    = JSON_UNQUOTE(JSON_EXTRACT(p_events, CONCAT('$[', v_idx, '].key')));
        SET v_data   = JSON_EXTRACT(p_events,    CONCAT('$[', v_idx, '].data'));
        CALL sp_events__publish(v_path, v_topic, v_key, v_data);
        SET v_idx = v_idx + 1;
    END WHILE;

END;

