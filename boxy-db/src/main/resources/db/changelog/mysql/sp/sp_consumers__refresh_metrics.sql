CREATE PROCEDURE sp_consumers__refresh_metrics(IN p_consumer_id VARCHAR(36), IN p_consumer_group_id BIGINT)
BEGIN
    DECLARE v_heartbeat_deadline_multiplier DOUBLE;
    DECLARE v_heartbeat_timeout_period DOUBLE;
    DECLARE v_heartbeat_interval DOUBLE;

    -- GET METRICS
    SELECT hp.heartbeat_deadline_multiplier,
           MAX(st.heartbeat_interval)
      INTO v_heartbeat_deadline_multiplier,
           v_heartbeat_interval
      FROM subscriptions st
      CROSS JOIN heartbeat_policies hp
     WHERE st.consumer_group_id = p_consumer_group_id;

    -- CONSUMER UPDATE
    SET v_heartbeat_timeout_period = GREATEST(1, CEILING(v_heartbeat_interval * v_heartbeat_deadline_multiplier));
    UPDATE consumers
       SET heartbeat_interval = v_heartbeat_interval,
           heartbeat_detected_at = CURRENT_TIMESTAMP(3),
           heartbeat_deadline = CURRENT_TIMESTAMP(3) + INTERVAL v_heartbeat_timeout_period SECOND
    WHERE  id = p_consumer_id;

END;