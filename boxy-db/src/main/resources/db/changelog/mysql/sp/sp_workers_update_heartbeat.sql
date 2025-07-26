CREATE PROCEDURE sp_workers_update_heartbeat(
    IN p_worker_id BIGINT,
    IN p_node_id VARCHAR(255),
    IN p_consumer_group_id BIGINT,
    IN p_weight INT
)
BEGIN
    -- Update worker heartbeat
    INSERT INTO workers (id, node_id, consumer_group_id, weight, last_heartbeat)
    VALUES (p_worker_id, p_node_id, p_consumer_group_id, p_weight, CURRENT_TIMESTAMP(3))
    ON DUPLICATE KEY UPDATE 
        weight = VALUES(weight),
        last_heartbeat = VALUES(last_heartbeat);
END;