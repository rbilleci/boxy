CREATE PROCEDURE sp_namespaces__create(
    IN p_parent_path VARCHAR(1024),
    IN p_name        VARCHAR(255))
BEGIN
    DECLARE v_parent_id BIGINT;
    DECLARE v_path VARCHAR(1024);
    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN RESIGNAL; END;

    IF p_parent_path IS NULL OR p_parent_path = '' THEN
        SET v_parent_id = NULL;
        SET v_path = p_name;
    ELSE
        SELECT id INTO v_parent_id FROM namespaces WHERE path = p_parent_path;
        SET v_path = CONCAT(p_parent_path, '/', p_name);
    END IF;

    INSERT INTO namespaces(name, parent_id, path) VALUES (p_name, v_parent_id, v_path);
    SELECT LAST_INSERT_ID() AS id;
END;
