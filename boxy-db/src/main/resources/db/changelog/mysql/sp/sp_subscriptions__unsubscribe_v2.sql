-- sp_subscriptions__unsubscribe  (v2 — EXIT HANDLER for SQLEXCEPTION)
--
-- === Item #77: Stored procedure error handling ===
--   Wraps the existing explicit transaction with a DECLARE EXIT HANDLER FOR
--   SQLEXCEPTION that ROLLBACKs and RESIGNALs.  Prevents partial deletes
--   (link removed but cascaded cursor rows not yet cleaned up) on error.
DROP PROCEDURE IF EXISTS sp_subscriptions__unsubscribe;
CREATE PROCEDURE sp_subscriptions__unsubscribe(
    IN p_subscription VARCHAR(500),
    IN p_path VARCHAR(4000),
    IN p_topic VARCHAR(500))
BEGIN
    DECLARE v_subscription_id BIGINT;
    DECLARE v_topic_id BIGINT;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;
        -- RESOLVE THE TOPIC ID
        SELECT id INTO v_topic_id FROM topics WHERE namespace_id = fn_resolve_namespace_id(p_path) AND name = p_topic;

        -- RESOLVE THE SUBSCRIPTION
        SELECT id INTO v_subscription_id FROM subscriptions WHERE name = p_subscription;

        -- DELETE LINK
        DELETE FROM subscription_topics WHERE subscription_id = v_subscription_id AND topic_id = v_topic_id;
    COMMIT;
END;
