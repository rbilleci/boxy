CREATE PROCEDURE sp_workers_check_in__heartbeat(
    IN p_worker_id VARCHAR(255),
    IN p_consumer_group_id BIGINT,
    IN p_weight DOUBLE,
    IN p_heartbeat_interval INT,
    IN p_heartbeat_deadline DATETIME(3)
)
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN RESIGNAL; END;

    INSERT INTO workers (
        id,
        consumer_group_id,
        weight,
        heartbeat_detected_at,
        heartbeat_interval,
        heartbeat_deadline)
    VALUES (
        p_worker_id,
        p_consumer_group_id,
        p_weight,
        CURRENT_TIMESTAMP(3),
        p_heartbeat_interval,
        p_heartbeat_deadline)
    ON DUPLICATE KEY UPDATE
        id                      = id,
        weight                  = VALUES(weight),
        heartbeat_detected_at   = VALUES(heartbeat_detected_at),
        heartbeat_interval      = VALUES(heartbeat_interval),
        heartbeat_deadline      = VALUES(heartbeat_deadline);
END;