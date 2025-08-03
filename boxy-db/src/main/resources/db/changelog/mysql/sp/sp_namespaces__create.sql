CREATE PROCEDURE sp_namespaces__create(
    IN p_parent_path VARCHAR(4000),
    IN p_name        VARCHAR(500))
BEGIN
    DECLARE v_parent_id BIGINT;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN RESIGNAL; END;

    IF p_parent_path IS NULL OR p_parent_path = '' THEN
        SET v_parent_id = NULL;
    ELSE
        SET v_parent_id = fn_resolve_namespace_id(p_parent_path, '/');
    END IF;

    INSERT INTO namespaces(name, parent_id) VALUES (p_name, v_parent_id);
    SELECT LAST_INSERT_ID() AS id;
END;
