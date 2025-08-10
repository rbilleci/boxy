CREATE PROCEDURE sp_events__publish_advanced(
    IN p_partition_id BIGINT,
    IN p_data JSON)
BEGIN
    -- EVENT PUBLICATION
    INSERT INTO events(partition_id, data) VALUES (p_partition_id, p_data);
END;

