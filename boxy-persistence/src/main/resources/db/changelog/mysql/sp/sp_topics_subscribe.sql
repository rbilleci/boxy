CREATE PROCEDURE sp_topics_subscribe(
    IN p_consumer_group_id BIGINT,
    IN p_topic_id BIGINT)
BEGIN
    DECLARE v_id BIGINT;
    INSERT INTO subscriptions(consumer_group_id, topic_id) VALUES (p_consumer_group_id, p_topic_id);
    SET v_id = LAST_INSERT_ID();
    INSERT INTO subscription_offsets(subscription_id, partition_id, committed_offset)
        SELECT v_id, id, 0 FROM partitions WHERE topic_id = p_topic_id;
    SELECT v_id AS id;
END;

