CREATE PROCEDURE sp_consumer_groups_delete(
    IN p_tenant VARCHAR(255),
    IN p_name VARCHAR(255))
BEGIN
    DELETE FROM consumer_groups WHERE tenant = p_tenant AND name = p_name;
END;

