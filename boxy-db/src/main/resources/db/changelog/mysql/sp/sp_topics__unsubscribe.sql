CREATE PROCEDURE sp_topics__unsubscribe(
    IN p_tenant VARCHAR(255),
    IN p_subscription VARCHAR(255),
    IN p_namespace_id BIGINT,
    IN p_topic VARCHAR(255))
BEGIN
    DECLARE v_subscription_id BIGINT;
    DECLARE v_topic_id BIGINT;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN RESIGNAL; END;

    -- RESOLVE THE TOPIC ID
    SELECT id INTO v_topic_id FROM topics WHERE namespace_id = p_namespace_id AND name = p_topic;

    -- RESOLVE THE SUBSCRIPTION
    SELECT id INTO v_subscription_id FROM subscriptions WHERE tenant = p_tenant AND name = p_subscription;

    -- DELETE LINK
    DELETE FROM subscription_topics WHERE subscription_id = v_subscription_id AND topic_id = v_topic_id;
END;

