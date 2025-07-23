CREATE PROCEDURE sp_consumer_groups_create(
    IN p_tenant VARCHAR(255),
    IN p_name VARCHAR(255))
BEGIN
    INSERT INTO consumer_groups(tenant, name) VALUES (p_tenant, p_name);
    SELECT LAST_INSERT_ID() AS id;
END;
