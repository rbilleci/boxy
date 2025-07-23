CREATE PROCEDURE sp_events_publish_multi(
    IN p_partition_id BIGINT,
    IN p_events JSON)
BEGIN
    INSERT INTO events(partition_id, data)
        SELECT p_partition_id, jt.data
        FROM JSON_TABLE(p_events, '$[*]' COLUMNS(data JSON PATH '$')) AS jt;
    UPDATE partitions SET high_watermark = GREATEST(high_watermark, LAST_INSERT_ID()) WHERE id = p_partition_id;
END;

