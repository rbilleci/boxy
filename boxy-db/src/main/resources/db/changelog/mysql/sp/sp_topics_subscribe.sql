CREATE PROCEDURE sp_topics_subscribe(
    IN p_tenant VARCHAR(255),
    IN p_consumer_group VARCHAR(255),
    IN p_topic VARCHAR(255))
BEGIN
    DECLARE v_consumer_group_id BIGINT;
    DECLARE v_topic_id BIGINT;
    DECLARE v_id BIGINT;

    -- RESOLVE THE TOPIC ID
    SELECT id INTO v_topic_id FROM topics WHERE tenant = p_tenant AND name = p_topic;

    -- RESOLVE THE CONSUMER GROUP ID
    SELECT id INTO v_consumer_group_id FROM consumer_groups WHERE tenant = p_tenant AND name = p_consumer_group;

    -- INSERT SUBSCRIPTION
    INSERT INTO subscriptions(consumer_group_id, topic_id) VALUES (v_consumer_group_id, v_topic_id);

    -- INSERT OFFSETS
    SET v_id = LAST_INSERT_ID();
    INSERT INTO subscription_offsets(subscription_id, partition_id, committed_offset)
        SELECT v_id, id, 0
        FROM partitions
        WHERE topic_id = v_topic_id;

    SELECT v_id AS id;
END;

