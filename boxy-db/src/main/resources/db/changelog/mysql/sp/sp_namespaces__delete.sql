CREATE PROCEDURE sp_namespaces__delete(IN p_path VARCHAR(4000))
BEGIN
    DELETE FROM namespaces WHERE id = fn_resolve_namespace_id(p_path);
END;
