DROP PROCEDURE IF EXISTS sp_subscribe_topics;
CREATE PROCEDURE sp_subscribe_topics(IN p_consumer_group_id BIGINT, IN p_topic_ids JSON)
BEGIN
    DECLARE idx INT DEFAULT 0;
    DECLARE len INT;
    SET len = JSON_LENGTH(p_topic_ids);
    WHILE idx < len DO
        CALL sp_subscribe(p_consumer_group_id, JSON_EXTRACT(p_topic_ids, CONCAT('$[', idx, ']')));
        SET idx = idx + 1;
    END WHILE;
END;

