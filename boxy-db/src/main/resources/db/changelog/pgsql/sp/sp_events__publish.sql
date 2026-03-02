CREATE OR REPLACE FUNCTION sp_events__publish(
    IN p_path  VARCHAR(4000),
    IN p_topic VARCHAR(500),
    IN p_key   VARCHAR(255),
    IN p_data  BYTEA)
    LANGUAGE plpgsql
AS $$
DECLARE
    v_partition_number    INT;
    v_partitions          INT;
    v_partition_id        BIGINT;
    v_topic_id            BIGINT;
    v_max_payload_bytes   BIGINT DEFAULT 1048576;
    v_event_id            BIGINT;
BEGIN
    BEGIN
        -- Item #99: validate payload size against configurable limit.
        SELECT COALESCE(MAX((config_value)::BIGINT), 1048576)
          INTO v_max_payload_bytes
          FROM boxy_config
         WHERE config_key = 'max.event.payload.bytes';

        IF OCTET_LENGTH(p_data) > v_max_payload_bytes THEN
            RAISE EXCEPTION 'PAYLOAD_TOO_LARGE';
        END IF;

        -- Resolve the topic and partition count
        CALL sp_topics__cache_get(p_path, p_topic, v_topic_id, v_partitions);
        v_partition_number := (('x' || SUBSTR(MD5(p_key), 1, 8))::BIT(32)::INT) % v_partitions;
        v_partition_id := fn_resolve_partition_id(v_topic_id, v_partition_number);

        -- Atomic event publication: both INSERTs or neither
        INSERT INTO events(data) VALUES (p_data)
        RETURNING id INTO v_event_id;

        INSERT INTO unprocessed_events(id, partition_id) VALUES (v_event_id, v_partition_id);

    EXCEPTION WHEN OTHERS THEN
        RAISE;
    END;
END;
$$;
