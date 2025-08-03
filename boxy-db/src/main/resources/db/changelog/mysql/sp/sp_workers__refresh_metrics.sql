CREATE PROCEDURE sp_workers__refresh_metrics(
    IN p_worker_id VARCHAR(36),
    IN p_subscription_id BIGINT
)
BEGIN
    DECLARE v_heartbeat_deadline_multiplier DOUBLE;
    DECLARE v_heartbeat_timeout_period DOUBLE;
    DECLARE v_heartbeat_interval DOUBLE;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN RESIGNAL; END;

    -- GET METRICS
    SELECT  heartbeat_deadline_multiplier,
            heartbeat_interval
      INTO  v_heartbeat_deadline_multiplier,
            v_heartbeat_interval
      FROM subscriptions
     WHERE id = p_subscription_id;

    -- WORKER UPDATE
    SET v_heartbeat_timeout_period = GREATEST(1, CEILING(v_heartbeat_interval * v_heartbeat_deadline_multiplier));
    UPDATE workers
       SET heartbeat_interval = v_heartbeat_interval,
           heartbeat_detected_at = CURRENT_TIMESTAMP(3),
           heartbeat_deadline = CURRENT_TIMESTAMP(3) + INTERVAL v_heartbeat_timeout_period SECOND
    WHERE  id = p_worker_id;

END;