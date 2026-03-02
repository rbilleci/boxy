-- sp_events__publish (v2 — EXIT HANDLER with ROLLBACK, Section 2 item 71)
DROP PROCEDURE IF EXISTS sp_events__publish;
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

    -- EXIT HANDLER: rolls back the open transaction on any SQL error and re-signals
    -- the error to the caller.  Without this handler, a partial INSERT into events
    -- without the matching unprocessed_events row would silently leave the table
    -- inconsistent.
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

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
