CREATE PROCEDURE sp_topics_subscribe(
    IN p_consumer_group_id BIGINT,
    IN p_topic_id BIGINT)
BEGIN
    INSERT INTO subscriptions(consumer_group_id, topic_id) VALUES (p_consumer_group_id, p_topic_id);
    SET @sub_id = LAST_INSERT_ID();
    INSERT INTO subscription_offsets(subscription_id, partition_id, committed_offset)
        SELECT @sub_id, id, 0 FROM partitions WHERE topic_id = p_topic_id;
    SELECT @sub_id AS id;
END;

