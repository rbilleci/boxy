CREATE OR REPLACE FUNCTION sp_consumers__register(
    IN p_consumer_id VARCHAR(36),
    IN p_subscription_name VARCHAR(500),
    IN p_topics JSONB)
    LANGUAGE plpgsql
AS $$
DECLARE
    v_subscription_id BIGINT;
    v_topic_ids JSONB;
    v_input_count INT;
    v_invalid_paths INT;
    v_missing_topics INT;
    v_missing_subscriptions INT;
    v_delimiter VARCHAR(10);
    v_delimiter_length INT;
    v_now TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3);
    v_existing_deadline TIMESTAMP(3);
BEGIN
    v_delimiter := fn_resolve_namespace_delimiter();
    v_delimiter_length := LENGTH(v_delimiter);

    IF jsonb_typeof(p_topics) <> 'array' THEN
        RAISE EXCEPTION 'p_topics must be a JSON array of topic paths';
    END IF;

    SELECT id INTO v_subscription_id FROM subscriptions WHERE name = p_subscription_name;

    IF v_subscription_id IS NULL THEN
        RAISE EXCEPTION 'Subscription does not exist';
    END IF;

    SELECT heartbeat_deadline INTO v_existing_deadline FROM consumers WHERE id = p_consumer_id;
    IF v_existing_deadline IS NOT NULL AND v_existing_deadline > v_now THEN
        RAISE EXCEPTION 'DUPLICATE_CONSUMER';
    END IF;

    -- Process topics using jsonb_array_elements and validate paths
    WITH topic_input AS (
        SELECT TRIM(jt.topic_path) AS topic_path
          FROM jsonb_array_elements_text(p_topics) jt(topic_path)
    ),
    path_checks AS (
        SELECT
            topic_path,
            CASE
                WHEN topic_path IS NULL OR TRIM(topic_path) = '' THEN 0
                WHEN topic_path NOT LIKE '%' || v_delimiter || '%' THEN 0
                WHEN SUBSTRING(topic_path, LENGTH(topic_path) - 
                               POSITION(REVERSE(v_delimiter) IN REVERSE(topic_path)) + 2) IS NULL 
                     OR TRIM(SUBSTRING(topic_path, LENGTH(topic_path) - 
                                      POSITION(REVERSE(v_delimiter) IN REVERSE(topic_path)) + 2)) = '' THEN 0
                WHEN LEFT(topic_path, LENGTH(topic_path) - LENGTH(SUBSTRING(topic_path, LENGTH(topic_path) - 
                          POSITION(REVERSE(v_delimiter) IN REVERSE(topic_path)) + 2)) - v_delimiter_length) IS NULL
                     OR TRIM(LEFT(topic_path, LENGTH(topic_path) - LENGTH(SUBSTRING(topic_path, LENGTH(topic_path) - 
                                  POSITION(REVERSE(v_delimiter) IN REVERSE(topic_path)) + 2)) - v_delimiter_length)) = '' THEN 0
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
        (SELECT jsonb_agg(topic_id)
           FROM (SELECT DISTINCT topic_id FROM resolved WHERE topic_id IS NOT NULL) AS deduped)
    INTO v_input_count, v_invalid_paths, v_missing_topics, v_missing_subscriptions, v_topic_ids;

    IF v_input_count = 0 THEN
        RAISE EXCEPTION 'At least one topic must be provided';
    END IF;

    IF v_invalid_paths > 0 THEN
        RAISE EXCEPTION 'Topic paths must include a namespace path and topic name separated by the namespace delimiter';
    END IF;

    IF v_missing_topics > 0 THEN
        RAISE EXCEPTION 'One or more topics do not exist';
    END IF;

    IF v_missing_subscriptions > 0 THEN
        RAISE EXCEPTION 'One or more topics are not part of the subscription';
    END IF;

    DELETE FROM consumers WHERE id = p_consumer_id;

    INSERT INTO consumers (
        id,
        subscription_id,
        heartbeat_detected_at,
        heartbeat_interval,
        heartbeat_deadline,
        topic_ids)
    VALUES (
        p_consumer_id,
        v_subscription_id,
        v_now,
        30,
        v_now + INTERVAL '30 seconds',
        COALESCE(v_topic_ids, '[]'::jsonb));
END;
$$;
