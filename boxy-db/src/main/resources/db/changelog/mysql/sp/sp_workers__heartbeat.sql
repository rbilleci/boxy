    CREATE PROCEDURE sp_workers__heartbeat(
    IN p_worker_id VARCHAR(255),
    IN p_consumer_group_id BIGINT,
    IN p_weight DOUBLE,
    OUT p_status VARCHAR(255)
)
BEGIN
    DECLARE v_exists BOOLEAN;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN RESIGNAL; END;

    -- UPDATE WORKER
    SELECT EXISTS(SELECT 1 FROM workers WHERE id = p_worker_id) INTO v_exists;
    IF v_exists THEN
        -- UPDATE WORKER
        UPDATE workers w
          JOIN consumer_groups cg ON cg.id = w.consumer_group_id
           SET w.weight = p_weight,
               w.heartbeat_detected_at = CURRENT_TIMESTAMP(3)
         WHERE w.id = p_worker_id;
        SET p_status = 'ACCEPTED';

    -- INSERT WORKER
    ELSE
        INSERT INTO workers (id, consumer_group_id, weight, heartbeat_interval, heartbeat_deadline)
        SELECT p_worker_id,
               p_consumer_group_id,
               p_weight,
               heartbeat_interval_baseline,
               CURRENT_TIMESTAMP(3) + INTERVAL 1 SECOND
          FROM consumer_groups
         WHERE active_workers < active_workers_limit
           AND id = p_consumer_group_id;
        SET p_status = IF(ROW_COUNT() > 0, 'ACCEPTED', 'REJECTED');
    END IF;

END;