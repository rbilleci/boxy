-- sp_subscriptions__delete  (v2 — EXIT HANDLER for SQLEXCEPTION)
--
-- === Item #77: Stored procedure error handling ===
--   Adds DECLARE EXIT HANDLER FOR SQLEXCEPTION so that unexpected errors
--   surface cleanly to the caller rather than being silently swallowed.
DROP PROCEDURE IF EXISTS sp_subscriptions__delete;
CREATE PROCEDURE sp_subscriptions__delete(IN p_name VARCHAR(500))
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        RESIGNAL;
    END;

    DELETE FROM subscriptions WHERE name = p_name;
END;
