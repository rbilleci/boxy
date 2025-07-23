CREATE PROCEDURE sp_events_publish_advanced(
    IN p_partition_id BIGINT,
    IN p_data JSON)
BEGIN
    DECLARE v_sequence BIGINT;
    -- EVENT PUBLICATION
    INSERT INTO events(partition_id, data) VALUES (p_partition_id, p_data);
    -- HWM UPDATE
    SET v_sequence = LAST_INSERT_ID();
    UPDATE partitions SET high_watermark = v_sequence WHERE id = p_partition_id AND high_watermark < v_sequence;
END;

