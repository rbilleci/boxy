CREATE PROCEDURE sp_consumers__subscribe(
    IN p_consumer_id VARCHAR(36),
    IN p_path VARCHAR(4000),
    IN p_topic VARCHAR(500))
BEGIN
    DECLARE v_topic_id BIGINT;
    DECLARE v_subscription_topic_id BIGINT;

    -- RESOLVE TOPIC AND SUBSCRIPTION
    SELECT id INTO v_topic_id
      FROM topics
     WHERE namespace_id = fn_resolve_namespace_id(p_path)
       AND name = p_topic;

    SELECT st.id INTO v_subscription_topic_id
      FROM subscription_topics st
      JOIN consumers w ON w.subscription_id = st.subscription_id
     WHERE w.id = p_consumer_id
       AND st.topic_id = v_topic_id;

    INSERT INTO consumer_subscriptions (consumer_id, subscription_topic_id, topic_id)
         VALUES (p_consumer_id, v_subscription_topic_id, v_topic_id)
    ON DUPLICATE KEY UPDATE topic_id = VALUES(topic_id);
END;
