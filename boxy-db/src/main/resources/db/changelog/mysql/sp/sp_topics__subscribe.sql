CREATE PROCEDURE sp_topics__subscribe(
    IN p_tenant VARCHAR(255),
    IN p_subscription VARCHAR(255),
    IN p_namespace  VARCHAR(255),
    IN p_topic VARCHAR(255))
BEGIN
    DECLARE v_subscription_id BIGINT;
    DECLARE v_topic_id BIGINT;
    DECLARE v_namespace_id BIGINT;
    DECLARE v_id BIGINT;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN RESIGNAL; END;

    START TRANSACTION;
        -- RESOLVE THE NAMESPACE ID
        SELECT id INTO v_namespace_id FROM namespaces WHERE name = p_namespace AND tenant = p_tenant;

        -- RESOLVE THE TOPIC ID
        SELECT id INTO v_topic_id FROM topics WHERE namespace_id = v_namespace_id AND name = p_topic;

        -- RESOLVE THE SUBSCRIPTION ID
        SELECT id INTO v_subscription_id FROM subscriptions WHERE tenant = p_tenant AND name = p_subscription;

        -- LINK SUBSCRIPTION TO TOPIC
        INSERT INTO subscription_topics(subscription_id, topic_id) VALUES (v_subscription_id, v_topic_id);

        -- INSERT OFFSETS
        SET v_id = LAST_INSERT_ID();
        INSERT INTO subscription_offsets(subscription_id, partition_id, random_key, committed_offset)
             SELECT v_id, id, fn_random_int(), 0
               FROM partitions
              WHERE topic_id = v_topic_id;
    COMMIT;

    SELECT v_id AS id;
END;

