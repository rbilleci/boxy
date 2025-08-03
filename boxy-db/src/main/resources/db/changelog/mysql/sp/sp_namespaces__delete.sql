CREATE PROCEDURE sp_namespaces__delete(
    IN p_path VARCHAR(4000))
BEGIN
    DECLARE v_id BIGINT;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN RESIGNAL; END;
    SET v_id = fn_resolve_namespace_id(p_path);
    IF v_id IS NOT NULL THEN
        DELETE FROM namespaces WHERE id = v_id;
    END IF;
END;
