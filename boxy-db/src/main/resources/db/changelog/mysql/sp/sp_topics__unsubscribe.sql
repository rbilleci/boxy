CREATE PROCEDURE sp_topics__unsubscribe(
    IN p_subscription VARCHAR(500),
    IN p_path VARCHAR(4000),
    IN p_topic VARCHAR(500))
BEGIN
    DECLARE v_subscription_id BIGINT;
    DECLARE v_topic_id BIGINT;
    DECLARE v_namespace_id BIGINT;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN RESIGNAL; END;

    -- RESOLVE THE NAMESPACE ID
    SET v_namespace_id = fn_resolve_namespace_id(p_path);

    -- RESOLVE THE TOPIC ID
    SELECT id INTO v_topic_id FROM topics WHERE namespace_id = v_namespace_id AND name = p_topic;

    -- RESOLVE THE SUBSCRIPTION
    SELECT id INTO v_subscription_id FROM subscriptions WHERE name = p_subscription;

    -- DELETE LINK
    DELETE FROM subscription_topics WHERE subscription_id = v_subscription_id AND topic_id = v_topic_id;
END;

