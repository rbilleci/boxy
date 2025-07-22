CREATE PROCEDURE sp_publish_event(IN p_partition_id BIGINT, IN p_event JSON)
BEGIN
    INSERT INTO events(partition_id, data) VALUES(p_partition_id, p_event);
END;

