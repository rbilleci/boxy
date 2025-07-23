CREATE PROCEDURE sp_events_publish(
    IN p_tenant VARCHAR(255),
    IN p_topic VARCHAR(255),
    IN p_key VARCHAR(255),
    IN p_data JSON)
BEGIN
    DECLARE v_partition_id BIGINT;
    DECLARE v_partition_number INT;
    DECLARE v_partitions INT;
    DECLARE v_topic_id BIGINT;
    DECLARE v_sequence BIGINT;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
        BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    -- RESOLVE THE TOPIC AND PARTITION COUNT
    CALL sp_topics_cache_get(p_tenant, p_topic, @topic_id, @partitions);
    SET v_partition_number = CRC32(p_key) % @partitions;
    CALL sp_partitions_cache_get(@topic_id, v_partition_number, @partition_id);

    START TRANSACTION;
        -- EVENT PUBLICATION
        INSERT INTO events(partition_id, data) VALUES (@partition_id, p_data);
        -- HWM UPDATE
        SET v_sequence = LAST_INSERT_ID();
        UPDATE partitions SET high_watermark = v_sequence WHERE id = @partition_id AND high_watermark < v_sequence;
    COMMIT;
END;