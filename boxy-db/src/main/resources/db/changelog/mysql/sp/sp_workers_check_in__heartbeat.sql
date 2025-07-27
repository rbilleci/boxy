CREATE PROCEDURE sp_workers_check_in__heartbeat(
    IN p_node_id VARCHAR(255),
    IN p_consumer_group_id BIGINT,
    IN p_weight INT,
    IN p_heartbeat_interval INT,
    IN p_heartbeat_deadline DATETIME(3),
    OUT p_worker_id BIGINT
)
BEGIN
    -- Perform an UPSERT on the worker
    INSERT INTO workers (node_id, consumer_group_id, weight, last_heartbeat, heartbeat_interval, heartbeat_deadline)
    VALUES (p_node_id, p_consumer_group_id, p_weight, CURRENT_TIMESTAMP(3), p_heartbeat_interval, p_heartbeat_deadline)
    ON DUPLICATE KEY UPDATE
        consumer_group_id = VALUES(consumer_group_id),
        weight = VALUES(weight),
        last_heartbeat = VALUES(last_heartbeat),
        heartbeat_interval = VALUES(heartbeat_interval),
        heartbeat_deadline = VALUES(heartbeat_deadline);
   -- RETURN THE WORKER ID
   SELECT id INTO p_worker_id FROM workers WHERE node_id = p_node_id;
END;