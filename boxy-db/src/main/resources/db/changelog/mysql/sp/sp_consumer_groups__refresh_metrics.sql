CREATE PROCEDURE sp_consumer_groups__refresh_metrics(IN p_consumer_group_id BIGINT)
BEGIN
    -- Variables for the aggregates
    DECLARE v_timestamp TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3);
    DECLARE v_active_consumers INT DEFAULT 0;
    DECLARE v_active_consumers_weight DOUBLE DEFAULT 0;
    DECLARE v_active_partitions INT DEFAULT 0;
    DECLARE v_heartbeat_interval DOUBLE;
    DECLARE v_heartbeat_interval_baseline DOUBLE;
    DECLARE v_heartbeat_interval_limit DOUBLE;
    DECLARE v_heartbeat_target_qps DOUBLE;

    -- ACTIVE CONSUMERS + TOTAL WEIGHT
    SELECT
        COUNT(w.id),
        COALESCE(SUM(w.weight), 0)
      INTO v_active_consumers, v_active_consumers_weight
      FROM consumers w
     WHERE w.consumer_group_id = p_consumer_group_id
       AND v_timestamp < w.heartbeat_deadline;

    -- ACTIVE PARTITIONS
    SELECT COUNT(1)
      INTO v_active_partitions
      FROM subscriptions st
      JOIN cursors c ON st.id = c.subscription_id
      JOIN partitions p ON c.partition_id = p.id
     WHERE c.position < p.high_watermark
       AND st.consumer_group_id = p_consumer_group_id;

    -- POLICY
    SELECT heartbeat_target_qps,
           heartbeat_interval_baseline,
           heartbeat_interval_limit
      INTO v_heartbeat_target_qps,
           v_heartbeat_interval_baseline,
           v_heartbeat_interval_limit
      FROM heartbeat_policies;

    -- VERIFY POLICY IS FOUND
    IF v_heartbeat_target_qps IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'No heartbeat policy found.';
    END IF;


    -- CALCULATE HEARTBEAT FROM TARGET QPS
    SET v_heartbeat_interval = v_heartbeat_interval_baseline;
    SET v_heartbeat_interval = GREATEST(v_heartbeat_interval, v_active_consumers /  NULLIF(v_heartbeat_target_qps, 0));
    SET v_heartbeat_interval = LEAST(v_heartbeat_interval, v_heartbeat_interval_limit);

    -- UPDATE
    UPDATE consumer_groups
       SET active_consumers = v_active_consumers,
           active_consumers_weight = v_active_consumers_weight,
           active_partitions = v_active_partitions,
           heartbeat_interval = v_heartbeat_interval
     WHERE id = p_consumer_group_id;

END;