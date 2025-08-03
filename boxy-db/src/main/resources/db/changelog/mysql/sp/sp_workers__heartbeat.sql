CREATE PROCEDURE sp_workers__heartbeat(
    IN p_worker_id VARCHAR(36),
    IN p_subscription_id BIGINT,
    IN p_weight DOUBLE,
    OUT p_status VARCHAR(255)
)
BEGIN
    DECLARE v_timestamp TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3);
    DECLARE v_exists BOOLEAN;

    -- UPDATE WORKER
    SELECT EXISTS(SELECT 1 FROM workers WHERE id = p_worker_id) INTO v_exists;
    IF v_exists THEN
        -- UPDATE WORKER
        UPDATE workers w
           SET w.weight = p_weight,
               w.heartbeat_detected_at = v_timestamp
         WHERE w.id = p_worker_id;
        SET p_status = 'ACCEPTED';

    -- INSERT WORKER
    ELSE
        INSERT INTO workers (id, subscription_id, weight, heartbeat_interval, heartbeat_deadline)
        SELECT p_worker_id,
               p_subscription_id,
               p_weight,
               heartbeat_interval_baseline,
               v_timestamp + INTERVAL 1 SECOND
          FROM subscriptions
         WHERE active_workers < active_workers_limit
           AND id = p_subscription_id;
        SET p_status = IF(ROW_COUNT() > 0, 'ACCEPTED', 'REJECTED');
    END IF;

END;