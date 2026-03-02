-- sp_events__publish (v3 — maximum event payload size validation)
--
-- === Item #99: Add maximum event payload size validation ===
--   Validates that the event data payload does not exceed the configured maximum.
--   The limit is read from boxy_config ('max.event.payload.bytes'), defaulting
--   to 1,048,576 bytes (1 MiB) if not configured.
--
--   Override at runtime:
--     UPDATE boxy_config SET config_value = '524288'
--      WHERE config_key = 'max.event.payload.bytes';  -- 512 KiB
--
--   SIGNAL SQLSTATE '45000' MESSAGE 'PAYLOAD_TOO_LARGE' is raised if the
--   payload exceeds the limit, allowing callers to distinguish this from
--   infrastructure failures via DataAccessException.hasErrorCode().
--
--   All other behaviour is identical to v2 (EXIT HANDLER, single INSERT).
DROP PROCEDURE IF EXISTS sp_events__publish;
CREATE PROCEDURE sp_events__publish(
    IN p_path  VARCHAR(4000),
    IN p_topic VARCHAR(500),
    IN p_key   VARCHAR(255),
    IN p_data  LONGBLOB
)
BEGIN
    DECLARE v_partition_number    INT;
    DECLARE v_partitions          INT;
    DECLARE v_partition_id        BIGINT;
    DECLARE v_topic_id            BIGINT;
    DECLARE v_max_payload_bytes   BIGINT DEFAULT 1048576;

    -- EXIT HANDLER: rolls back the open transaction on any SQL error and re-signals
    -- the error to the caller.  Without this handler, a partial INSERT into events
    -- without the matching unprocessed_events row would silently leave the table
    -- inconsistent.
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    -- Item #99: validate payload size against configurable limit.
    SELECT COALESCE(MAX(CAST(config_value AS SIGNED)), 1048576)
      INTO v_max_payload_bytes
      FROM boxy_config
     WHERE config_key = 'max.event.payload.bytes';

    IF LENGTH(p_data) > v_max_payload_bytes THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'PAYLOAD_TOO_LARGE';
    END IF;

    -- Resolve the topic and partition count
    CALL sp_topics__cache_get(p_path, p_topic, v_topic_id, v_partitions);
    SET v_partition_number = CRC32(p_key) % v_partitions;
    SET v_partition_id = fn_resolve_partition_id(v_topic_id, v_partition_number);

    -- Atomic event publication: both INSERTs or neither
    START TRANSACTION;
        INSERT INTO events(data) VALUES (p_data);
        INSERT INTO unprocessed_events(id, partition_id) VALUES (LAST_INSERT_ID(), v_partition_id);
    COMMIT;
END;
