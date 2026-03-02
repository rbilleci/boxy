-- sp_subscriptions__create  (v2 — EXIT HANDLER for SQLEXCEPTION)
--
-- === Item #77: Stored procedure error handling ===
--   Adds DECLARE EXIT HANDLER FOR SQLEXCEPTION so that unexpected errors
--   (e.g. duplicate subscription name) surface cleanly to the caller
--   rather than being silently swallowed.
DROP PROCEDURE IF EXISTS sp_subscriptions__create;
CREATE PROCEDURE sp_subscriptions__create(IN p_name VARCHAR(500))
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        RESIGNAL;
    END;

    INSERT INTO subscriptions(name) VALUES (p_name);
    SELECT LAST_INSERT_ID() AS id;
END;
