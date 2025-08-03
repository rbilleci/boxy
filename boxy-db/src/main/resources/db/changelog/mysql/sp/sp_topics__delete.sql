CREATE PROCEDURE sp_topics__delete(
    IN p_path VARCHAR(4000),
    IN p_name VARCHAR(500))
BEGIN
    DECLARE v_namespace_id BIGINT;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN RESIGNAL; END;
    -- RESOLVE THE NAMESPACE ID
    SELECT id INTO v_namespace_id FROM namespaces WHERE path_hash = UNHEX(MD5(p_path)) AND path = p_path;
    -- DELETE
    DELETE FROM topics WHERE namespace_id = v_namespace_id AND name = p_name;
END;

