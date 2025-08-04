CREATE PROCEDURE sp_consumer_groups__unsubscribe(
    IN p_consumer_group VARCHAR(500),
    IN p_path VARCHAR(4000),
    IN p_topic VARCHAR(500))
BEGIN
    DECLARE v_consumer_group_id BIGINT;
    DECLARE v_topic_id BIGINT;

    START TRANSACTION;
        -- RESOLVE THE TOPIC ID
        SELECT id INTO v_topic_id FROM topics WHERE namespace_id = fn_resolve_namespace_id(p_path) AND name = p_topic;

        -- RESOLVE THE CONSUMER GROUP
        SELECT id INTO v_consumer_group_id FROM consumer_groups WHERE name = p_consumer_group;

        -- DELETE LINK
        DELETE FROM subscriptions WHERE consumer_group_id = v_consumer_group_id AND topic_id = v_topic_id;
    COMMIT;
END;

