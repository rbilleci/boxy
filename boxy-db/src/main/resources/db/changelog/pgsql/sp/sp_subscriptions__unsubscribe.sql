CREATE OR REPLACE FUNCTION sp_subscriptions__unsubscribe(
    IN p_subscription VARCHAR(500),
    IN p_path VARCHAR(4000),
    IN p_topic VARCHAR(500))
    LANGUAGE plpgsql
AS $$
DECLARE
    v_subscription_id BIGINT;
    v_topic_id BIGINT;
BEGIN
    BEGIN
        -- RESOLVE THE TOPIC ID
        SELECT id INTO v_topic_id FROM topics WHERE namespace_id = fn_resolve_namespace_id(p_path) AND name = p_topic;

        -- RESOLVE THE SUBSCRIPTION
        SELECT id INTO v_subscription_id FROM subscriptions WHERE name = p_subscription;

        -- DELETE LINK
        DELETE FROM subscription_topics WHERE subscription_id = v_subscription_id AND topic_id = v_topic_id;

    EXCEPTION WHEN OTHERS THEN
        RAISE;
    END;
END;
$$;
