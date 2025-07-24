CREATE PROCEDURE sp_topics_delete(
    IN p_tenant VARCHAR(255),
    IN p_name VARCHAR(255))
BEGIN
    DELETE FROM topics WHERE tenant = p_tenant AND name = p_name;
END;

