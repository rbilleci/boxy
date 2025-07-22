DROP PROCEDURE IF EXISTS sp_publish_events;
CREATE PROCEDURE sp_publish_events(IN p_partition_id BIGINT, IN p_events JSON)
BEGIN
    INSERT INTO events(partition_id, data)
    SELECT p_partition_id, jt.data
    FROM JSON_TABLE(p_events, '$[*]' COLUMNS(data JSON PATH '$')) AS jt;
END;

