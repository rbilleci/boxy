CREATE PROCEDURE sp_namespaces__delete(IN p_path VARCHAR(4000))
BEGIN
    DECLARE v_id BIGINT;
    SET v_id = fn_resolve_namespace_id(p_path);
    DELETE FROM namespaces WHERE id IN (
        SELECT descendant_id FROM namespace_closures WHERE ancestor_id = v_id
    );
END;
