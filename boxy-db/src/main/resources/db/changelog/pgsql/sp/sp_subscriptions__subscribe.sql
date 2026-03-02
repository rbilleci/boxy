CREATE OR REPLACE FUNCTION sp_subscriptions__subscribe(
    IN p_subscription VARCHAR(500),
    IN p_path  VARCHAR(4000),
    IN p_topic VARCHAR(500),
    OUT v_id BIGINT)
    LANGUAGE plpgsql
AS $$
DECLARE
    v_subscription_id    BIGINT;
    v_topic_id           BIGINT;
    v_heartbeat_interval DOUBLE PRECISION DEFAULT 15.0;
BEGIN
    v_id := NULL;

    BEGIN
        -- Item #93: read default heartbeat interval from boxy_config.
        SELECT COALESCE(MAX(CAST(config_value AS DECIMAL(10,3))), 15.0)
          INTO v_heartbeat_interval
          FROM boxy_config
         WHERE config_key = 'heartbeat.interval.seconds';

        -- RESOLVE THE TOPIC ID
        SELECT id INTO v_topic_id FROM topics WHERE namespace_id = fn_resolve_namespace_id(p_path) AND name = p_topic;

        -- RESOLVE THE SUBSCRIPTION ID
        SELECT id INTO v_subscription_id FROM subscriptions WHERE name = p_subscription;

        -- LINK SUBSCRIPTION TO TOPIC (explicit heartbeat_interval from config)
        INSERT INTO subscription_topics(subscription_id, topic_id, heartbeat_interval)
        VALUES (v_subscription_id, v_topic_id, v_heartbeat_interval)
        RETURNING id INTO v_id;

        -- INSERT CURSORS
        INSERT INTO cursors(topic_id, subscription_topic_id, subscription_id, partition_id, random_key, position)
             SELECT v_topic_id, v_id, v_subscription_id, id, fn_random_int(), 0
               FROM partitions
              WHERE topic_id = v_topic_id;

    EXCEPTION WHEN OTHERS THEN
        RAISE;
    END;
END;
$$;
