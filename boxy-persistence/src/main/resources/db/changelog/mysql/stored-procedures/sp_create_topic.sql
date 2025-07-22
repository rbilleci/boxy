DROP PROCEDURE IF EXISTS sp_create_topic;
CREATE PROCEDURE sp_create_topic(IN p_tenant VARCHAR(255), IN p_name VARCHAR(255), IN p_partitions INT)
BEGIN
    INSERT INTO topics(tenant, name, partitions) VALUES (p_tenant, p_name, p_partitions);
    SET @topic_id = LAST_INSERT_ID();
    WITH RECURSIVE numbers(n) AS (
        SELECT 0 UNION ALL SELECT n+1 FROM numbers WHERE n < p_partitions - 1
    )
    INSERT INTO partitions(topic_id, partition_number)
    SELECT @topic_id, n FROM numbers;
    SELECT @topic_id AS id;
END;

