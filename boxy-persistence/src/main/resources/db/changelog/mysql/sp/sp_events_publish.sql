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

    -- RESOLVE THE TOPIC
    SELECT id, partitions INTO v_topic_id, v_partitions
        FROM topics
        WHERE
            tenant = p_tenant AND
            name = p_topic;

    SET v_partition_number = CRC32(p_key) % v_partitions;

    -- RESOLVE THE PARTITION
    SELECT id INTO v_partition_id FROM partitions
    WHERE
        topic_id = v_topic_id AND
        partition_number = v_partition_number
    LIMIT 1;

    START TRANSACTION;
        -- EVENT PUBLICATION
        INSERT INTO events(partition_id, data) VALUES (v_partition_id, p_data);
        -- HWM UPDATE
        SET v_sequence = LAST_INSERT_ID();
        UPDATE partitions SET high_watermark = v_sequence WHERE id = v_partition_id AND high_watermark < v_sequence;
    COMMIT;
END;