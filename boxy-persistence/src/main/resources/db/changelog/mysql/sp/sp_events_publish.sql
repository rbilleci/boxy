CREATE PROCEDURE sp_events_publish(
    IN p_tenant_name VARCHAR(255),
    IN p_topic_name VARCHAR(255),
    IN p_key VARCHAR(255),
    IN p_event JSON)
BEGIN
    DECLARE v_partition_id BIGINT;
    DECLARE v_key_hash INT UNSIGNED;

    SET v_key_hash = CRC32(p_key);

    SELECT id INTO v_partition_id FROM partitions
    WHERE
        tenant_name = p_tenant_name AND
        topic_name = p_topic_name AND
        partition_number = (v_key_hash % partitions)
    LIMIT 1;

    INSERT INTO events(partition_id, data) VALUES (v_partition_id, p_event);
    UPDATE partitions SET high_watermark = GREATEST(high_watermark, LAST_INSERT_ID()) WHERE id = v_partition_id;

END;