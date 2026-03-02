-- sp_topics__delete  (v2 — EXIT HANDLER for SQLEXCEPTION)
--
-- === Item #78: Stored procedure error handling ===
--   Adds DECLARE EXIT HANDLER FOR SQLEXCEPTION so that unexpected errors
--   surface cleanly to the caller rather than being silently swallowed.
DROP PROCEDURE IF EXISTS sp_topics__delete;
CREATE PROCEDURE sp_topics__delete(IN p_path VARCHAR(4000), IN p_name VARCHAR(500))
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        RESIGNAL;
    END;

    DELETE FROM topics WHERE namespace_id = fn_resolve_namespace_id(p_path) AND name = p_name;
END;
