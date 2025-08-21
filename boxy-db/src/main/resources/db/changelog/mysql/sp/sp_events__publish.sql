CREATE PROCEDURE sp_events__publish(
    IN p_path VARCHAR(4000),
    IN p_topic VARCHAR(500),
    IN p_key VARCHAR(255),
    IN p_data LONGBLOB)
BEGIN
    DECLARE v_partition_number INT;
    DECLARE v_partitions INT;
    DECLARE v_partition_id BIGINT;
    DECLARE v_topic_id BIGINT;
    DECLARE v_event_id BIGINT;

    -- RESOLVE THE TOPIC AND PARTITION COUNT
    CALL sp_topics__cache_get(p_path, p_topic, v_topic_id, v_partitions);
    SET v_partition_number = CRC32(p_key) % v_partitions;
    SET v_partition_id = fn_resolve_partition_id(v_topic_id, v_partition_number);

    -- EVENT PUBLICATION
    START TRANSACTION;
        INSERT INTO events(data) VALUES (p_data);
        SET v_event_id = LAST_INSERT_ID();
        INSERT INTO unprocessed_events(id, partition_id) VALUES (v_event_id, v_partition_id);
    COMMIT;
END;
