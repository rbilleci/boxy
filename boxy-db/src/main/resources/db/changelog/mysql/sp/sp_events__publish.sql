-- Publishes an event
-- Expected to be called from within a transaction.
CREATE PROCEDURE sp_events__publish(
    IN p_tenant VARCHAR(255),
    IN p_namespace VARCHAR(255),
    IN p_topic VARCHAR(255),
    IN p_key VARCHAR(255),
    IN p_data JSON)
BEGIN
    DECLARE v_partition_number INT;
    DECLARE v_partition_id BIGINT;
    DECLARE v_sequence BIGINT;

    -- RESOLVE THE TOPIC AND PARTITION COUNT
    CALL sp_topics__cache_get(p_tenant, p_namespace, p_topic, @topic_id, @partitions);
    SET v_partition_number = CRC32(p_key) % @partitions;
    SET v_partition_id = fn_resolve_partition_id(@topic_id, v_partition_number);

    -- EVENT PUBLICATION
    INSERT INTO events(partition_id, data) VALUES (v_partition_id, p_data);
    -- HWM UPDATE
    SET v_sequence = LAST_INSERT_ID();
    UPDATE partitions SET high_watermark = v_sequence WHERE id = v_partition_id AND high_watermark < v_sequence;
END;