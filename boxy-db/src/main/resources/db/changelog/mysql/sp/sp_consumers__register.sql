CREATE PROCEDURE sp_consumers__register(
    IN p_consumer_id VARCHAR(36),
    IN p_subscription_name VARCHAR(500),
    IN p_topics JSON)
BEGIN
    DECLARE v_subscription_id BIGINT;
    DECLARE v_topic_ids JSON;
    DECLARE v_input_count INT;
    DECLARE v_invalid_paths INT;
    DECLARE v_missing_topics INT;
    DECLARE v_delimiter VARCHAR(10);
    DECLARE v_delimiter_length INT;

    SET v_delimiter = fn_resolve_namespace_delimiter();
    SET v_delimiter_length = CHAR_LENGTH(v_delimiter);

    IF JSON_TYPE(p_topics) <> 'ARRAY' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'p_topics must be a JSON array of topic paths';
    END IF;

    SELECT id INTO v_subscription_id FROM subscriptions WHERE name = p_subscription_name;

    WITH topic_input AS (
        SELECT TRIM(jt.topic_path) AS topic_path
          FROM JSON_TABLE(p_topics, '$[*]' COLUMNS(topic_path VARCHAR(4000) PATH '$')) AS jt
    ),
    path_checks AS (
        SELECT
            topic_path,
            CASE
                WHEN topic_path IS NULL OR TRIM(topic_path) = '' THEN 0
                WHEN topic_path NOT LIKE CONCAT('%', v_delimiter, '%') THEN 0
                WHEN SUBSTRING_INDEX(topic_path, v_delimiter, -1) IS NULL OR TRIM(SUBSTRING_INDEX(topic_path, v_delimiter, -1)) = '' THEN 0
                WHEN LEFT(topic_path, CHAR_LENGTH(topic_path) - CHAR_LENGTH(SUBSTRING_INDEX(topic_path, v_delimiter, -1)) - v_delimiter_length) IS NULL
                     OR TRIM(LEFT(topic_path, CHAR_LENGTH(topic_path) - CHAR_LENGTH(SUBSTRING_INDEX(topic_path, v_delimiter, -1)) - v_delimiter_length)) = '' THEN 0
                ELSE 1
            END AS is_valid
          FROM topic_input
    ),
    resolved AS (
        SELECT
            pc.topic_path,
            pc.is_valid,
            fn_resolve_topic_id(pc.topic_path) AS topic_id
          FROM path_checks AS pc
    )
    SELECT
        (SELECT COUNT(*) FROM topic_input),
        (SELECT COUNT(*) FROM resolved WHERE is_valid = 0),
        (SELECT COUNT(*) FROM resolved WHERE is_valid = 1 AND topic_id IS NULL),
        (SELECT JSON_ARRAYAGG(DISTINCT topic_id ORDER BY topic_id) FROM resolved WHERE topic_id IS NOT NULL)
    INTO v_input_count, v_invalid_paths, v_missing_topics, v_topic_ids;

    IF v_input_count = 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'At least one topic must be provided';
    END IF;

    IF v_invalid_paths > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Topic paths must include a namespace path and topic name separated by the namespace delimiter';
    END IF;

    IF v_missing_topics > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'One or more topics do not exist';
    END IF;

    INSERT INTO consumers (
        id,
        subscription_id,
        weight,
        heartbeat_detected_at,
        heartbeat_interval,
        heartbeat_deadline,
        topic_ids)
    VALUES (
        p_consumer_id,
        v_subscription_id,
        1.0,
        CURRENT_TIMESTAMP(3),
        30,
        DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 30 SECOND),
        v_topic_ids)
    ON DUPLICATE KEY UPDATE
        subscription_id = VALUES(subscription_id),
        heartbeat_detected_at = VALUES(heartbeat_detected_at),
        heartbeat_deadline = VALUES(heartbeat_deadline),
        topic_ids = VALUES(topic_ids);
END;
