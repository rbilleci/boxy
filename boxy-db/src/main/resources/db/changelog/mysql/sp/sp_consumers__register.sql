CREATE PROCEDURE sp_consumers__register(
    IN p_session_id VARCHAR(36),
    IN p_subscription_name VARCHAR(500),
    IN p_topics JSON)
BEGIN
    DECLARE v_subscription_id BIGINT;
    DECLARE v_topic_ids JSON;
    DECLARE v_input_count INT;
    DECLARE v_invalid_paths INT;
    DECLARE v_missing_topics INT;
    DECLARE v_missing_subscriptions INT;
    DECLARE v_delimiter VARCHAR(10);
    DECLARE v_delimiter_length INT;
    DECLARE v_now DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3);
    DECLARE v_existing_deadline DATETIME(3);

    SET v_delimiter = fn_resolve_namespace_delimiter();
    SET v_delimiter_length = CHAR_LENGTH(v_delimiter);

    IF JSON_TYPE(p_topics) <> 'ARRAY' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'p_topics must be a JSON array of topic paths';
    END IF;

    SELECT id INTO v_subscription_id FROM subscriptions WHERE name = p_subscription_name;

    IF v_subscription_id IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Subscription does not exist';
    END IF;

    SELECT heartbeat_deadline INTO v_existing_deadline FROM consumers WHERE id = p_session_id;
    IF v_existing_deadline IS NOT NULL AND v_existing_deadline > v_now THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'DUPLICATE_SESSION';
    END IF;

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
        (SELECT COUNT(*) FROM resolved WHERE topic_id IS NOT NULL AND topic_id NOT IN (
            SELECT topic_id FROM subscription_topics WHERE subscription_id = v_subscription_id
        )),
        (SELECT JSON_ARRAYAGG(topic_id)
           FROM (SELECT DISTINCT topic_id FROM resolved WHERE topic_id IS NOT NULL) AS deduped)
    INTO v_input_count, v_invalid_paths, v_missing_topics, v_missing_subscriptions, v_topic_ids;

    IF v_input_count = 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'At least one topic must be provided';
    END IF;

    IF v_invalid_paths > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Topic paths must include a namespace path and topic name separated by the namespace delimiter';
    END IF;

    IF v_missing_topics > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'One or more topics do not exist';
    END IF;

    IF v_missing_subscriptions > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'One or more topics are not part of the subscription';
    END IF;

    DELETE FROM consumers WHERE id = p_session_id;

    INSERT INTO consumers (
        id,
        subscription_id,
        weight,
        heartbeat_detected_at,
        heartbeat_interval,
        heartbeat_deadline,
        topic_ids)
    VALUES (
        p_session_id,
        v_subscription_id,
        1.0,
        v_now,
        30,
        DATE_ADD(v_now, INTERVAL 30 SECOND),
        v_topic_ids);
END;
