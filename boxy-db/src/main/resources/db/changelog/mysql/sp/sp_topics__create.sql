CREATE PROCEDURE sp_topics__create(
    IN p_tenant VARCHAR(255),
    IN p_name VARCHAR(255),
    IN p_partitions INT)
BEGIN
    DECLARE v_topic_id BIGINT;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; END;

    START TRANSACTION;
        INSERT INTO topics (tenant, name, partitions) VALUES (p_tenant, p_name, p_partitions);
        SET v_topic_id = LAST_INSERT_ID();

        -- INSERT THE PARTITIONS
        INSERT INTO partitions (topic_id, partition_number)
        WITH RECURSIVE numbers (n) AS (
            SELECT 0
            UNION ALL
            SELECT n + 1 FROM numbers WHERE n < p_partitions - 1)
        SELECT v_topic_id, n FROM numbers;
    COMMIT;

    -- RETURN THE TOPIC ID
    SELECT v_topic_id AS id;
END;
