CREATE PROCEDURE sp_workers_check_in__update_stats(
    IN p_consumer_group_id BIGINT,
    OUT p_active_partitions INT,
    OUT p_active_workers INT,
    OUT p_active_workers_weight DOUBLE,
    OUT p_heartbeat_interval DOUBLE
)
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN RESIGNAL; END;

    -- Get heartbeat configuration values from consumer_groups
    SELECT heartbeat_interval_default,
           heartbeat_target_qps,
           heartbeat_interval_min,
           heartbeat_interval_max,
           statistics_refresh_interval,
           active_partitions,
           active_workers,
           active_workers_weight,
           last_updated
      INTO p_heartbeat_interval,
           @target_qps,
           @interval_min,
           @interval_max,
           @stats_refresh_interval,
           p_active_partitions,
           p_active_workers,
           p_active_workers_weight,
           @stats_last_updated
      FROM consumer_groups
     WHERE id = p_consumer_group_id;

    -- Update the stats in the consumer_groups table if they are stale
    IF TIMESTAMPDIFF(SECOND, @stats_last_updated, CURRENT_TIMESTAMP(3)) >= @stats_refresh_interval THEN

        -- ACTIVE WORKERS
        SELECT COUNT(1), COALESCE(SUM(weight), 0)
            INTO p_active_workers, p_active_workers_weight
            FROM workers
           WHERE consumer_group_id = p_consumer_group_id
             AND heartbeat_deadline > CURRENT_TIMESTAMP(3);

        -- ACTIVE PARTITIONS (where high_watermark > committed_offset)
        SELECT COUNT(1)
          INTO p_active_partitions
          FROM subscriptions s
          JOIN subscription_offsets so ON s.id = so.subscription_id
          JOIN partitions p ON so.partition_id = p.id
         WHERE s.consumer_group_id = p_consumer_group_id
           AND so.committed_offset < p.high_watermark;

        -- Calculate adaptive heartbeat interval based on heartbeat_interval_default
        -- Use the configurable heartbeat_target_qps
        -- This controls the ideal cluster-wide check-ins per second
        SET p_heartbeat_interval = GREATEST(p_heartbeat_interval, p_active_workers / @target_qps);

        -- Apply min and max constraints to the heartbeat interval
        SET p_heartbeat_interval = GREATEST(p_heartbeat_interval, @interval_min);
        SET p_heartbeat_interval = LEAST(p_heartbeat_interval, @interval_max);

        -- Update consumer group statistics
        UPDATE consumer_groups
           SET active_workers        = p_active_workers,
               active_workers_weight = p_active_workers_weight,
               active_partitions     = p_active_partitions,
               last_updated          = CURRENT_TIMESTAMP(3)
        WHERE id = p_consumer_group_id;
    END IF;

END;