DROP PROCEDURE IF EXISTS sp_register_worker;
CREATE PROCEDURE sp_register_worker(IN p_node_id VARCHAR(255), IN p_consumer_group_id BIGINT, IN p_weight INT)
BEGIN
    INSERT INTO workers(node_id, consumer_group_id, weight) VALUES (p_node_id, p_consumer_group_id, p_weight);
    SELECT LAST_INSERT_ID() AS id;
END;

