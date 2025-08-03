CREATE PROCEDURE sp_topics__delete(
    IN p_path VARCHAR(4000),
    IN p_name VARCHAR(500))
BEGIN
    DECLARE v_namespace_id BIGINT;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN RESIGNAL; END;
    -- RESOLVE THE NAMESPACE ID
    CALL sp_namespaces__resolve_id(p_path, '/', v_namespace_id);
    -- DELETE
    DELETE FROM topics WHERE namespace_id = v_namespace_id AND name = p_name;
END;

