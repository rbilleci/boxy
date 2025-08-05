CREATE PROCEDURE sp_subscription_topics__refresh_metrics(IN p_subscription_id BIGINT)
BEGIN
    DECLARE v_timestamp TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3);
    DECLARE v_heartbeat_interval_baseline DOUBLE;
    DECLARE v_heartbeat_interval_limit DOUBLE;
    DECLARE v_heartbeat_target_qps DOUBLE;

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

    -- UPDATE SUBSCRIPTION TOPICS WITH CALCULATED METRICS
    UPDATE subscription_topics st
    LEFT JOIN (
        SELECT cs.subscription_topic_id,
               COUNT(w.id) AS active_consumers,
               COALESCE(SUM(w.weight), 0) AS active_consumers_weight
          FROM consumer_subscriptions cs
          JOIN consumers w ON w.id = cs.consumer_id
         WHERE v_timestamp < w.heartbeat_deadline
         GROUP BY cs.subscription_topic_id
    ) s ON st.id = s.subscription_topic_id
    LEFT JOIN (
        SELECT c.subscription_topic_id,
               COUNT(1) AS active_partitions
          FROM cursors c
          JOIN partitions p ON c.partition_id = p.id
         WHERE c.position < p.high_watermark
         GROUP BY c.subscription_topic_id
    ) a ON st.id = a.subscription_topic_id
       SET st.active_consumers = COALESCE(s.active_consumers, 0),
           st.active_consumers_weight = COALESCE(s.active_consumers_weight, 0),
           st.active_partitions = COALESCE(a.active_partitions, 0),
           st.heartbeat_interval = LEAST(
                GREATEST(v_heartbeat_interval_baseline,
                         COALESCE(s.active_consumers, 0) / NULLIF(v_heartbeat_target_qps, 0)),
                v_heartbeat_interval_limit)
     WHERE st.subscription_id = p_subscription_id;
END;
