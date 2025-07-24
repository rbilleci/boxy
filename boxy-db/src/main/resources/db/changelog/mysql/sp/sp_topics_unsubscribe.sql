CREATE PROCEDURE sp_topics_unsubscribe(
    IN p_tenant VARCHAR(255),
    IN p_consumer_group VARCHAR(255),
    IN p_topic VARCHAR(255))
BEGIN
    DECLARE v_consumer_group_id BIGINT;
    DECLARE v_topic_id BIGINT;
    DECLARE v_id BIGINT;

    -- RESOLVE THE TOPIC ID
    SELECT id INTO v_topic_id FROM topics WHERE tenant = p_tenant AND name = p_topic;

    -- RESOLVE THE CONSUMER GROUP
    SELECT id INTO v_consumer_group_id FROM consumer_groups WHERE tenant = p_tenant AND name = p_consumer_group;

    -- DELETE SUBSCRIPTIONS
    DELETE FROM subscriptions WHERE consumer_group_id = v_consumer_group_id AND topic_id = v_topic_id;
END;

