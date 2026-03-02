-- sp_subscriptions__subscribe  (v3 — configurable default heartbeat_interval from boxy_config)
--
-- === Item #93: Make default heartbeat_interval configurable ===
--   v2 used the subscription_topics column DEFAULT (15.0 seconds).
--   v3 reads 'heartbeat.interval.seconds' from boxy_config, falling back
--   to 15 seconds if the key is absent.  Override:
--     UPDATE boxy_config SET config_value = '30'
--      WHERE config_key = 'heartbeat.interval.seconds';
--
--   All other behaviour is identical to v2 (EXIT HANDLER, transactional).
DROP PROCEDURE IF EXISTS sp_subscriptions__subscribe;
CREATE PROCEDURE sp_subscriptions__subscribe(
    IN p_subscription VARCHAR(500),
    IN p_path  VARCHAR(4000),
    IN p_topic VARCHAR(500))
BEGIN
    DECLARE v_subscription_id    BIGINT;
    DECLARE v_topic_id           BIGINT;
    DECLARE v_id                 BIGINT;
    DECLARE v_heartbeat_interval DOUBLE DEFAULT 15.0;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    -- Item #93: read default heartbeat interval from boxy_config.
    SELECT COALESCE(MAX(CAST(config_value AS DECIMAL(10,3))), 15.0)
      INTO v_heartbeat_interval
      FROM boxy_config
     WHERE config_key = 'heartbeat.interval.seconds';

    START TRANSACTION;
        -- RESOLVE THE TOPIC ID
        SELECT id INTO v_topic_id FROM topics WHERE namespace_id = fn_resolve_namespace_id(p_path) AND name = p_topic;

        -- RESOLVE THE SUBSCRIPTION ID
        SELECT id INTO v_subscription_id FROM subscriptions WHERE name = p_subscription;

        -- LINK SUBSCRIPTION TO TOPIC (explicit heartbeat_interval from config)
        INSERT INTO subscription_topics(subscription_id, topic_id, heartbeat_interval)
        VALUES (v_subscription_id, v_topic_id, v_heartbeat_interval);

        -- INSERT CURSORS
        SET v_id = LAST_INSERT_ID();
        INSERT INTO cursors(topic_id, subscription_topic_id, subscription_id, partition_id, random_key, position)
             SELECT v_topic_id, v_id, v_subscription_id, id, fn_random_int(), 0
               FROM partitions
              WHERE topic_id = v_topic_id;

    COMMIT;

    SELECT v_id AS id;
END;
