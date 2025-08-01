CREATE PROCEDURE sp_subscriptions__refresh_metrics(IN p_subscription_id BIGINT)
BEGIN
    -- Variables for the aggregates
    DECLARE v_active_workers INT DEFAULT 0;
    DECLARE v_active_workers_weight DOUBLE DEFAULT 0;
    DECLARE v_active_partitions INT DEFAULT 0;
    DECLARE v_heartbeat_interval DOUBLE;
    DECLARE v_heartbeat_interval_baseline DOUBLE;
    DECLARE v_heartbeat_interval_limit DOUBLE;
    DECLARE v_heartbeat_target_qps DOUBLE;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN RESIGNAL; END;

    -- ACTIVE WORKERS + TOTAL WEIGHT
    SELECT
        COUNT(w.id),
        COALESCE(SUM(w.weight), 0)
      INTO v_active_workers, v_active_workers_weight
      FROM workers w
     WHERE w.subscription_id = p_subscription_id
       AND CURRENT_TIMESTAMP(3) < w.heartbeat_deadline;

    -- ACTIVE PARTITIONS
    SELECT COUNT(1)
      INTO v_active_partitions
      FROM subscription_topics st
      JOIN subscription_offsets so ON st.id = so.subscription_id
      JOIN partitions p ON so.partition_id = p.id
     WHERE so.committed_offset < p.high_watermark
       AND st.subscription_id = p_subscription_id;

    -- METRICS
    SELECT heartbeat_target_qps,
           heartbeat_interval_baseline,
           heartbeat_interval_limit
      INTO v_heartbeat_target_qps,
           v_heartbeat_interval_baseline,
           v_heartbeat_interval_limit
      FROM subscriptions
     WHERE id = p_subscription_id;


    -- CALCULATE HEARTBEAT FROM TARGET QPS
    SET v_heartbeat_interval = v_heartbeat_interval_baseline;
    SET v_heartbeat_interval = GREATEST(v_heartbeat_interval, v_active_workers /  NULLIF(v_heartbeat_target_qps, 0));
    SET v_heartbeat_interval = LEAST(v_heartbeat_interval, v_heartbeat_interval_limit);

    -- UPDATE
    UPDATE subscriptions
       SET active_workers = v_active_workers,
           active_workers_weight = v_active_workers_weight,
           active_partitions = v_active_partitions,
           heartbeat_interval = v_heartbeat_interval,
           last_modified_at = CURRENT_TIMESTAMP(3)
     WHERE id = p_subscription_id;

END;