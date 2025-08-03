CREATE PROCEDURE sp_namespaces__delete(
    IN p_path VARCHAR(4000))
BEGIN
    DECLARE v_id BIGINT;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN RESIGNAL; END;
    SELECT id INTO v_id FROM namespaces WHERE path_hash = UNHEX(MD5(p_path)) AND path = p_path;
    IF v_id IS NOT NULL THEN
        DELETE FROM namespaces WHERE id = v_id;
    END IF;
END;
