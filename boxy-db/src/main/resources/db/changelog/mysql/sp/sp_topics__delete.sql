CREATE PROCEDURE sp_topics__delete(
    IN p_tenant VARCHAR(255),
    IN p_namespace VARCHAR(255),
    IN p_name VARCHAR(255))
BEGIN
    DECLARE v_namespace_id BIGINT;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN RESIGNAL; END;
    -- RESOLVE THE NAMESPACE ID
    SELECT id INTO v_namespace_id FROM namespaces WHERE name = p_namespace AND tenant = p_tenant;
    -- DELETE
    DELETE FROM topics WHERE namespace_id = v_namespace_id AND name = p_name;
END;

