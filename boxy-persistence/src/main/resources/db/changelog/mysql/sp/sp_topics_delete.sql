CREATE PROCEDURE sp_topics_delete(
    IN p_topic_id BIGINT)
BEGIN
    DELETE FROM topics WHERE id = p_topic_id;
END;

