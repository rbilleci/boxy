CREATE PROCEDURE sp_namespaces__create(
    IN p_tenant VARCHAR(255),
    IN p_name VARCHAR(255))
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN RESIGNAL; END;
    INSERT INTO namespaces(tenant, name) VALUES (p_tenant, p_name);
    SELECT LAST_INSERT_ID() AS id;
END;

