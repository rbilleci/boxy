CREATE PROCEDURE sp_events_publish_multi(
    IN p_events JSON)
BEGIN
    DECLARE v_idx INT DEFAULT 0;
    DECLARE v_len INT;
    DECLARE v_tenant VARCHAR(255);
    DECLARE v_topic  VARCHAR(255);
    DECLARE v_key  VARCHAR(255);
    DECLARE v_data   JSON;

    -- N ELEMENTS
    SET v_len = JSON_LENGTH(p_events);

    -- LOOP
    WHILE v_idx < v_len DO
        SET v_tenant = JSON_UNQUOTE(JSON_EXTRACT(p_events, CONCAT('$[', v_idx, '].tenant')));
        SET v_topic  = JSON_UNQUOTE(JSON_EXTRACT(p_events, CONCAT('$[', v_idx, '].topic')));
        SET v_key    = JSON_UNQUOTE(JSON_EXTRACT(p_events, CONCAT('$[', v_idx, '].key')));
        SET v_data   = JSON_EXTRACT(p_events,    CONCAT('$[', v_idx, '].data'));
        CALL sp_events_publish(v_tenant, v_topic, v_key, v_data);
        SET v_idx = v_idx + 1;
    END WHILE;

END;

