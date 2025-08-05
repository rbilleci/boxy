CREATE PROCEDURE sp_subscriptions__create(IN p_name VARCHAR(500))
BEGIN
    INSERT INTO subscriptions(name) VALUES (p_name);
    SELECT LAST_INSERT_ID() AS id;
END;
