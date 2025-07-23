CREATE PROCEDURE sp_topics_create(
    IN p_tenant VARCHAR(255),
    IN p_name VARCHAR(255),
    IN p_partitions INT)
BEGIN
    DECLARE v_id BIGINT;

    INSERT INTO topics (tenant, name, partitions) VALUES (p_tenant, p_name, p_partitions);
    SET v_id = LAST_INSERT_ID();

    -- INSERT THE PARTITIONS
    INSERT INTO partitions (tenant_name, topic_id, topic_name, partition_number, partitions)
    WITH RECURSIVE numbers (n) AS (
        SELECT 0
        UNION ALL
        SELECT n + 1 FROM numbers WHERE n < p_partitions - 1)
    SELECT p_tenant, v_id, p_name, n, p_partitions FROM numbers;
    -- RETURN THE TOPIC ID
    SELECT v_id AS id;
END;
