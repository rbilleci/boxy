CREATE PROCEDURE sp_consumers__heartbeat(
    IN p_consumer_id VARCHAR(36),
    IN p_consumer_group_id BIGINT,
    IN p_weight DOUBLE,
    OUT p_status VARCHAR(255)
)
BEGIN
    DECLARE v_timestamp TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3);
    DECLARE v_exists BOOLEAN;

    -- UPDATE CONSUMER
    SELECT EXISTS(SELECT 1 FROM consumers WHERE id = p_consumer_id) INTO v_exists;
    IF v_exists THEN
        -- UPDATE CONSUMER
        UPDATE consumers w
           SET w.weight = p_weight,
               w.heartbeat_detected_at = v_timestamp
         WHERE w.id = p_consumer_id;
        SET p_status = 'ACCEPTED';

    -- INSERT CONSUMER
    ELSE
        INSERT INTO consumers (id, consumer_group_id, weight, heartbeat_interval, heartbeat_deadline)
        SELECT p_consumer_id,
               p_consumer_group_id,
               p_weight,
               hp.heartbeat_interval_baseline,
               v_timestamp + INTERVAL 1 SECOND
          FROM (SELECT COALESCE(MAX(active_consumers),0) AS active_consumers
                  FROM subscriptions
                 WHERE consumer_group_id = p_consumer_group_id) cg
          CROSS JOIN heartbeat_policies hp
          CROSS JOIN lease_policies lp
         WHERE cg.active_consumers < lp.active_consumers_limit;
        SET p_status = IF(ROW_COUNT() > 0, 'ACCEPTED', 'REJECTED');
    END IF;

END;