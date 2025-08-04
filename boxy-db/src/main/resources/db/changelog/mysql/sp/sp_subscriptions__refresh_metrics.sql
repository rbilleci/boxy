CREATE PROCEDURE sp_subscriptions__refresh_metrics(IN p_consumer_group_id BIGINT)
BEGIN
    DECLARE v_timestamp TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3);
    DECLARE v_active_consumers INT DEFAULT 0;
    DECLARE v_active_consumers_weight DOUBLE DEFAULT 0;
    DECLARE v_heartbeat_interval DOUBLE;
    DECLARE v_heartbeat_interval_baseline DOUBLE;
    DECLARE v_heartbeat_interval_limit DOUBLE;
    DECLARE v_heartbeat_target_qps DOUBLE;

    -- ACTIVE CONSUMERS + TOTAL WEIGHT
    SELECT COUNT(w.id), COALESCE(SUM(w.weight), 0)
      INTO v_active_consumers, v_active_consumers_weight
      FROM consumers w
     WHERE w.consumer_group_id = p_consumer_group_id
       AND v_timestamp < w.heartbeat_deadline;

    -- POLICY
    SELECT heartbeat_target_qps,
           heartbeat_interval_baseline,
           heartbeat_interval_limit
      INTO v_heartbeat_target_qps,
           v_heartbeat_interval_baseline,
           v_heartbeat_interval_limit
      FROM heartbeat_policies;

    IF v_heartbeat_target_qps IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'No heartbeat policy found.';
    END IF;

    SET v_heartbeat_interval = v_heartbeat_interval_baseline;
    SET v_heartbeat_interval = GREATEST(v_heartbeat_interval, v_active_consumers / NULLIF(v_heartbeat_target_qps, 0));
    SET v_heartbeat_interval = LEAST(v_heartbeat_interval, v_heartbeat_interval_limit);

    -- UPDATE SUBSCRIPTIONS WITH CALCULATED METRICS
    UPDATE subscriptions st
       SET active_consumers = v_active_consumers,
           active_consumers_weight = v_active_consumers_weight,
           active_partitions = (
                SELECT COUNT(1)
                  FROM cursors c
                  JOIN partitions p ON c.partition_id = p.id
                 WHERE c.subscription_id = st.id
                   AND c.position < p.high_watermark
           ),
           heartbeat_interval = v_heartbeat_interval
     WHERE st.consumer_group_id = p_consumer_group_id;
END;
