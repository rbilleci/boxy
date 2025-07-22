CREATE PROCEDURE sp_unsubscribe(IN p_consumer_group_id BIGINT, IN p_topic_ids JSON)
BEGIN
    DELETE FROM subscriptions
    WHERE consumer_group_id = p_consumer_group_id
      AND topic_id IN (
        SELECT CAST(j.value AS UNSIGNED)
        FROM JSON_TABLE(p_topic_ids, '$[*]' COLUMNS(value BIGINT PATH '$')) AS j
      );
END;

