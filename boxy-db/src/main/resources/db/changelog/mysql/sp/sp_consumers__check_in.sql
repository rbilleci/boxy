CREATE PROCEDURE sp_consumers__check_in(
    IN p_consumer_id VARCHAR(36),
    IN p_consumer_group_id BIGINT,
    IN p_weight DOUBLE
)
BEGIN
    DECLARE v_status VARCHAR(255);
    DECLARE v_active_partitions INT;
    DECLARE v_leases_active INT DEFAULT 0;
    DECLARE v_leases_target DOUBLE;
    DECLARE v_leases_min INT;
    DECLARE v_leases_max INT;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN RESIGNAL; END;

    -- HEARTBEAT
    CALL sp_consumers__heartbeat(p_consumer_id, p_consumer_group_id, p_weight, v_status);
    CALL sp_consumers__gc(p_consumer_group_id);
    CALL sp_consumer_groups__refresh_metrics(p_consumer_group_id);

    -- HANDLE CONSUMER ACCEPTANCE
    IF v_status = 'ACCEPTED' THEN

        CALL sp_consumers__refresh_metrics(p_consumer_id, p_consumer_group_id);
        CALL sp_consumers__heartbeat(p_consumer_id, p_consumer_group_id, p_weight, v_status);

        -- ACTIVE LEASES:
        SELECT COUNT(1)
          INTO v_leases_active
          FROM leases
         WHERE consumer_id = p_consumer_id
           AND state = 'ACTIVE';

       -- IDEAL W/SLACK OF 10%
        SELECT COUNT(1)
          INTO v_leases_active
          FROM consumer_groups
         WHERE id = p_consumer_group_id;

        -- CALCULATE TARGET FOR LEASES
        SELECT (p_weight / active_consumers_weight * active_partitions),
               active_partitions
          INTO v_leases_target,
               v_active_partitions
          FROM consumer_groups
         WHERE id = p_consumer_group_id;

        SET v_leases_min = GREATEST(FLOOR(v_leases_target * 0.9), 0);
        SET v_leases_max = LEAST(CEILING(v_leases_target * 1.1), v_active_partitions);

        -- RELEASE / ACQUIRE
        IF (v_leases_active > v_leases_max) THEN
            CALL sp_leases__release_excess(p_consumer_id, p_consumer_group_id, v_leases_active - v_leases_max);
        ELSEIF (v_leases < v_leases_target) THEN
            CALL sp_leases__acquire(p_consumer_id, p_consumer_group_id, v_leases_target - v_leases_active);
        END IF;
    END IF;


    -- RETURN CHECK IN RESULT
    SELECT  heartbeat_interval,
            heartbeat_deadline,
            v_status AS status
      FROM  consumers
     WHERE  id = p_consumer_id;

    -- RETURN ACTIVE LEASES
    SELECT *
      FROM leased_cursors_view
     WHERE consumer_id = p_consumer_id;

END;
