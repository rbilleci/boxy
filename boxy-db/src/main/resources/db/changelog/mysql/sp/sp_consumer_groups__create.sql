CREATE PROCEDURE sp_consumer_groups__create(IN p_name VARCHAR(500))
BEGIN
    INSERT INTO consumer_groups(name) VALUES (p_name);
    SELECT LAST_INSERT_ID() AS id;
END;
