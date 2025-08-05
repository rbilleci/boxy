CREATE PROCEDURE sp_subscriptions__subscribe(
    IN p_subscription VARCHAR(500),
    IN p_path  VARCHAR(4000),
    IN p_topic VARCHAR(500))
BEGIN
    DECLARE v_subscription_id BIGINT;
    DECLARE v_topic_id BIGINT;
    DECLARE v_id BIGINT;

    START TRANSACTION;
        -- RESOLVE THE TOPIC ID
        SELECT id INTO v_topic_id FROM topics WHERE namespace_id = fn_resolve_namespace_id(p_path) AND name = p_topic;

        -- RESOLVE THE SUBSCRIPTION ID
        SELECT id INTO v_subscription_id FROM subscriptions WHERE name = p_subscription;

        -- LINK SUBSCRIPTION TO TOPIC
        INSERT INTO subscription_topics(subscription_id, topic_id) VALUES (v_subscription_id, v_topic_id);

        -- INSERT CURSORS
        SET v_id = LAST_INSERT_ID();
        INSERT INTO cursors(topic_id, subscription_topic_id, subscription_id, partition_id, random_key, position)
             SELECT v_topic_id, v_id, v_subscription_id, id, fn_random_int(), 0
               FROM partitions
              WHERE topic_id = v_topic_id;
    COMMIT;

    SELECT v_id AS id;
END;

