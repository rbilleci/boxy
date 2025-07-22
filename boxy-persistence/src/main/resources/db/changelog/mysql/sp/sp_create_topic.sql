CREATE PROCEDURE sp_create_topic(
    IN p_tenant VARCHAR(255),
    IN p_name VARCHAR(255),
    IN p_partitions INT)
BEGIN
    INSERT INTO topics(tenant, name, partitions) VALUES (p_tenant, p_name, p_partitions);
    SET @topic_id = LAST_INSERT_ID();
    -- INSERT THE PARTITIONS
    INSERT INTO partitions (topic_id, partition_number)
    WITH RECURSIVE numbers (n) AS (
        SELECT 0
        UNION ALL
        SELECT n + 1 FROM numbers WHERE n < p_partitions - 1)
    SELECT @topic_id, n FROM numbers;
    -- RETURN THE TOPIC ID
    SELECT @topic_id AS id;
END;

