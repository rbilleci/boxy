-- sp_events__publish_advanced (v2 — EXIT HANDLER with ROLLBACK, Section 2 item 72)
DROP PROCEDURE IF EXISTS sp_events__publish_advanced;
CREATE PROCEDURE sp_events__publish_advanced(
    IN p_partition_id BIGINT,
    IN p_data LONGBLOB)
BEGIN
    DECLARE v_event_id BIGINT;

    -- EXIT HANDLER: rolls back the transaction and re-signals on any SQL error.
    -- v1 had no transaction — this v2 adds an explicit START TRANSACTION so that
    -- a failure between the two INSERTs does not leave a dangling events row.
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;
        INSERT INTO events(data) VALUES (p_data);
        SET v_event_id = LAST_INSERT_ID();
        INSERT INTO unprocessed_events(id, partition_id) VALUES (v_event_id, p_partition_id);
    COMMIT;
END;
