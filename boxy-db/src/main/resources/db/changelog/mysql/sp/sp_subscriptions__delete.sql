CREATE PROCEDURE sp_subscriptions__delete(IN p_name VARCHAR(500))
BEGIN
    DELETE FROM subscriptions WHERE name = p_name;
END;

