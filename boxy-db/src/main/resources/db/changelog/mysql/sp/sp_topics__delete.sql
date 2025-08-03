CREATE PROCEDURE sp_topics__delete(IN p_path VARCHAR(4000), IN p_name VARCHAR(500))
BEGIN
    DELETE FROM topics WHERE namespace_id = fn_resolve_namespace_id(p_path) AND name = p_name;
END;

