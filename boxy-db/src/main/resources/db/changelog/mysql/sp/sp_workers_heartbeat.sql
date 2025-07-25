CREATE PROCEDURE sp_workers_heartbeat(
    IN p_node_id VARCHAR(255),
    IN p_consumer_group_id BIGINT)
BEGIN
    UPDATE workers 
    SET last_heartbeat = CURRENT_TIMESTAMP(3)
    WHERE node_id = p_node_id AND consumer_group_id = p_consumer_group_id;
    
    SELECT ROW_COUNT() AS updated;
END;