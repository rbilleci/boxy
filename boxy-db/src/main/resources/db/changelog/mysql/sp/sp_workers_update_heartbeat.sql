CREATE PROCEDURE sp_workers_update_heartbeat(
    IN p_node_id VARCHAR(255),
    IN p_consumer_group_id BIGINT,
    IN p_weight INT,
    OUT p_worker_id BIGINT
)
BEGIN
    -- This statement performs an "upsert" operation.
    -- It attempts to insert a new worker record. If the insert fails
    -- because a record with the same 'node_id' already exists
    -- (due to a UNIQUE constraint on the 'node_id' column),
    -- it then updates the existing record instead.
    INSERT INTO workers (node_id, consumer_group_id, weight, last_heartbeat)
    VALUES (p_node_id, p_consumer_group_id, p_weight, CURRENT_TIMESTAMP(3))
    ON DUPLICATE KEY UPDATE
        consumer_group_id = VALUES(consumer_group_id),
        weight = VALUES(weight),
        last_heartbeat = VALUES(last_heartbeat);

   -- RETURN THE WORKER ID
   SELECT id INTO p_worker_id FROM workers WHERE node_id = p_node_id;
END;