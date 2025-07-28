CREATE PROCEDURE sp_workers_check_in__update_stats(
    IN p_consumer_group_id BIGINT,
    OUT p_active_partitions INT,
    OUT p_active_workers INT,
    OUT p_active_workers_weight DOUBLE,
    OUT p_heartbeat_interval DOUBLE
)
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN RESIGNAL; END;

    -- ACTIVE WORKERS
    SELECT COUNT(1), COALESCE(SUM(weight), 0)
        INTO p_active_workers, p_active_workers_weight
        FROM workers
       WHERE consumer_group_id = p_consumer_group_id
         AND heartbeat_deadline > CURRENT_TIMESTAMP(3);

    -- ACTIVE PARTITIONS (where high_watermark > committed_offset)
    SELECT COUNT(1)
      INTO p_active_partitions
      FROM subscription_offsets_view so
     INNER JOIN subscriptions s ON s.id = so.subscription_id
     WHERE s.consumer_group_id = p_consumer_group_id
        AND so.high_watermark > so.committed_offset;

    -- Get heartbeat configuration values from consumer_groups
    SELECT heartbeat_interval_default, heartbeat_qps_target, heartbeat_interval_min, heartbeat_interval_max
      INTO p_heartbeat_interval, @qps_target, @interval_min, @interval_max
      FROM consumer_groups
     WHERE id = p_consumer_group_id;

    -- Calculate adaptive heartbeat interval based on heartbeat_interval_default
    -- Use the configurable heartbeat_qps_target instead of hardcoded 10.0
    -- This controls the cluster-wide check-ins per second
    SET p_heartbeat_interval = GREATEST(p_heartbeat_interval, p_active_workers / @qps_target);
    
    -- Apply min and max constraints to the heartbeat interval
    SET p_heartbeat_interval = GREATEST(p_heartbeat_interval, @interval_min);
    SET p_heartbeat_interval = LEAST(p_heartbeat_interval, @interval_max);

    -- Update the stats in the consumer_groups table
    UPDATE consumer_groups
       SET active_workers        = p_active_workers,
           active_workers_weight = p_active_workers_weight,
          active_partitions      = p_active_partitions,
          last_updated             = CURRENT_TIMESTAMP(3)
    WHERE id = p_consumer_group_id;

END;