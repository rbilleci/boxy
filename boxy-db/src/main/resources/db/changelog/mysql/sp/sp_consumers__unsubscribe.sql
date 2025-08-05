CREATE PROCEDURE sp_consumers__unsubscribe(
    IN p_consumer_id VARCHAR(36),
    IN p_path VARCHAR(4000),
    IN p_topic VARCHAR(500))
BEGIN
    DECLARE v_topic_id BIGINT;

    SELECT id INTO v_topic_id
      FROM topics
     WHERE namespace_id = fn_resolve_namespace_id(p_path)
       AND name = p_topic;

    DELETE FROM consumer_subscriptions
     WHERE consumer_id = p_consumer_id
       AND topic_id = v_topic_id;
END;
