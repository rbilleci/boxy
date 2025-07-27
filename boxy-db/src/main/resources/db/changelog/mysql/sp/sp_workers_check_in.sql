CREATE PROCEDURE sp_workers_check_in(
    IN p_node_id VARCHAR(255),
    IN p_consumer_group_id BIGINT,
    IN p_weight INT
)
BEGIN
    DECLARE v_active_workers INT DEFAULT 0;
    DECLARE v_total_weight INT DEFAULT 0;
    DECLARE v_active_partitions INT DEFAULT 0;
    DECLARE v_current_leases INT DEFAULT 0;
    DECLARE v_ideal_share DECIMAL(10,2);
    DECLARE v_slack DECIMAL(10,2) DEFAULT 0.10; -- 10% SLACK
    DECLARE v_min_leases INT;
    DECLARE v_max_leases INT;
    DECLARE v_leases_released INT DEFAULT 0;
    DECLARE v_leases_acquired INT DEFAULT 0;
    DECLARE v_worker_id BIGINT;
    DECLARE v_heartbeat_interval DOUBLE;
    DECLARE v_heartbeat_interval_default DOUBLE;
    DECLARE v_heartbeat_deadline DATETIME(3);
    DECLARE v_heartbeat_deadline_multiplier DOUBLE;
    DECLARE v_heartbeat_deadline_seconds INT;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN RESIGNAL; END;
    
    -- Start transaction to ensure consistency
    START TRANSACTION;

        -- Get the heartbeat_interval_default and heartbeat_deadline_multiplier from the consumer_groups table.
        SELECT heartbeat_interval_default,  heartbeat_deadline_multiplier
          INTO v_heartbeat_interval_default, v_heartbeat_deadline_multiplier
          FROM consumer_groups
         WHERE id = p_consumer_group_id;

        -- Compute the initial heartbeat deadline, with a minimum of 1-second for the heartbeat deadline.
        SET v_heartbeat_deadline_seconds = GREATEST(1, CEILING(v_heartbeat_interval_default * v_heartbeat_deadline_multiplier));
        SET v_heartbeat_deadline = CURRENT_TIMESTAMP(3) + INTERVAL v_heartbeat_deadline_seconds SECOND;

        -- Perform a heartbeat for this worker.
        -- The worker will be inserted with a default deadline if it does not already exist
        CALL sp_workers_check_in__heartbeat(
            p_node_id,
            p_consumer_group_id,
            p_weight,
            v_heartbeat_interval_default,
            v_heartbeat_deadline,
            v_worker_id);
        IF v_worker_id IS NULL THEN SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'No worker_id returned from sp_workers_check_in__heartbeat';
        END IF;

        -- Perform a garbage collection
        -- As this worker submitted a heartbeat already, it won't be removed.
        CALL sp_workers_check_in__gc(p_consumer_group_id);

        -- Recompute consumer group and worker statistics
        CALL sp_workers_check_in__update_stats(
            p_consumer_group_id,
            v_total_weight,
            v_active_partitions,
            v_active_workers,
            v_heartbeat_interval);

        -- Recompute the worker's heartbeat deadline (based on the actual `v_heartbeat_interval` value)
        SET v_heartbeat_deadline_seconds = GREATEST(1, CEILING(v_heartbeat_interval * v_heartbeat_deadline_multiplier));
        SET v_heartbeat_deadline = CURRENT_TIMESTAMP(3) + INTERVAL v_heartbeat_deadline_seconds SECOND;

        -- Update the worker's heartbeat interval and deadline
        CALL sp_workers_check_in__heartbeat(
            p_node_id,
            p_consumer_group_id,
            p_weight,
            v_heartbeat_interval,
            v_heartbeat_deadline,
            v_worker_id);

        -- Count ACTIVE leases for this worker (excluding those in a RELEASING state)
        SELECT COUNT(1)
            INTO v_current_leases
            FROM leases l
           WHERE l.worker_id = v_worker_id
             AND l.state = 'ACTIVE';

        -- Compute ideal share based on weight
        IF v_total_weight > 0 AND v_active_partitions > 0 THEN
            SET v_ideal_share = (p_weight / v_total_weight) * v_active_partitions;
        ELSE
            SET v_ideal_share = 0;
        END IF;

        -- Compute min and max leases with slack
       SET v_min_leases = GREATEST(
           FLOOR(v_ideal_share * (1.0 - v_slack)),
           0);
       SET v_max_leases = LEAST(
           CEILING(v_ideal_share * (1.0 + v_slack)),
           v_active_partitions);

        -- Release leases if we are over the fair share
        CALL sp_workers_check_in__release_leases(
            v_worker_id,
            p_consumer_group_id,
            v_max_leases,
            v_current_leases,
            v_leases_released
        );

        -- Acquire leases if we are under our fair share
        CALL sp_workers_check_in__acquire_leases(
            v_worker_id,
            p_consumer_group_id,
            v_min_leases,
            v_current_leases - v_leases_released,
            v_leases_acquired
        );

    COMMIT;

    -- Return stats for the worker to calculate next check-in time
    SELECT
        v_worker_id as worker_id,
        v_total_weight AS total_weight,
        v_active_partitions AS active_partitions,
        v_active_workers AS active_workers,
        p_weight AS worker_weight,
        v_ideal_share AS ideal_share,
        v_current_leases - v_leases_released + v_leases_acquired AS current_leases,
        v_min_leases AS min_leases,
        v_max_leases AS max_leases,
        v_heartbeat_interval AS heartbeat_interval,
        v_heartbeat_deadline AS heartbeat_deadline;

    -- Return all active leases for this worker (excluding those in a RELEASING state)
    SELECT
        so.id AS subscription_offset_id,
        so.subscription_id,
        so.partition_id,
        so.committed_offset,
        so.high_watermark
    FROM leases l
    INNER JOIN subscription_offsets_view so ON so.id = l.subscription_offset_id
    WHERE l.worker_id = v_worker_id
      AND l.state = 'ACTIVE';
END;