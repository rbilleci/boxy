CREATE PROCEDURE sp_events__publish(
    IN p_path VARCHAR(4000),
    IN p_topic VARCHAR(500),
    IN p_key VARCHAR(255),
    IN p_data JSON)
BEGIN
    DECLARE v_partition_number INT;
    DECLARE v_partitions INT;
    DECLARE v_partition_id BIGINT;
    DECLARE v_topic_id BIGINT;
    DECLARE v_sequence BIGINT;

    -- RESOLVE THE TOPIC AND PARTITION COUNT
    CALL sp_topics__cache_get(p_path, p_topic, v_topic_id, v_partitions);
    SET v_partition_number = CRC32(p_key) % v_partitions;
    SET v_partition_id = fn_resolve_partition_id(v_topic_id, v_partition_number);

    -- EVENT PUBLICATION
    INSERT INTO events(partition_id, data) VALUES (v_partition_id, p_data);

    -- HWM UPDATE
    SET v_sequence = LAST_INSERT_ID();
    UPDATE partitions SET high_watermark = v_sequence WHERE id = v_partition_id AND high_watermark < v_sequence;
END;