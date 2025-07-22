CREATE PROCEDURE sp_delete_topic_by_id(IN p_topic_id BIGINT)
BEGIN
    DELETE FROM topics WHERE id = p_topic_id;
END;

