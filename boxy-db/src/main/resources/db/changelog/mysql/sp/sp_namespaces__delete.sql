CREATE PROCEDURE sp_namespaces__delete(
    IN p_path VARCHAR(4000))
BEGIN
    DECLARE v_id BIGINT;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN RESIGNAL; END;
    CALL sp_namespaces__resolve_id(p_path, '/', v_id);
    IF v_id IS NOT NULL THEN
        DELETE FROM namespaces WHERE id = v_id;
    END IF;
END;
