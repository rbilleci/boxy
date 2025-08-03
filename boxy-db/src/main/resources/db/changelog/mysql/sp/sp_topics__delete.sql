CREATE PROCEDURE sp_topics__delete(
    IN p_path VARCHAR(1024),
    IN p_name VARCHAR(255))
BEGIN
    DECLARE v_namespace_id BIGINT;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN RESIGNAL; END;
    -- RESOLVE THE NAMESPACE ID
    SET v_namespace_id = fn_resolve_namespace_id(p_path);
    -- DELETE
    DELETE FROM topics WHERE namespace_id = v_namespace_id AND name = p_name;
END;

