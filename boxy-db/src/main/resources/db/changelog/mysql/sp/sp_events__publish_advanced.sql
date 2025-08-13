CREATE PROCEDURE sp_events__publish_advanced(
    IN p_partition_id BIGINT,
    IN p_data LONGBLOB)
BEGIN
    DECLARE v_event_id BIGINT;
    -- EVENT PUBLICATION
    INSERT INTO events(data) VALUES (p_data);
    SET v_event_id = LAST_INSERT_ID();
    INSERT INTO unprocessed_events(id, partition_id) VALUES (v_event_id, p_partition_id);
END;

