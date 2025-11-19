CREATE PROCEDURE sp_consumers__register(
    IN p_consumer_id VARCHAR(36),
    IN p_subscription_name VARCHAR(500))
BEGIN
    DECLARE v_subscription_id BIGINT;

    SELECT id INTO v_subscription_id FROM subscriptions WHERE name = p_subscription_name;

    INSERT INTO consumers (
        id,
        subscription_id,
        weight,
        heartbeat_detected_at,
        heartbeat_interval,
        heartbeat_deadline)
    VALUES (
        p_consumer_id,
        v_subscription_id,
        1.0,
        CURRENT_TIMESTAMP(3),
        30,
        DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 30 SECOND))
    ON DUPLICATE KEY UPDATE
        subscription_id = VALUES(subscription_id),
        heartbeat_detected_at = VALUES(heartbeat_detected_at),
        heartbeat_deadline = VALUES(heartbeat_deadline);
END;
