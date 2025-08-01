CREATE PROCEDURE sp_subscriptions__delete(
    IN p_tenant VARCHAR(255),
    IN p_name VARCHAR(255))
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN RESIGNAL; END;
    DELETE FROM subscriptions WHERE tenant = p_tenant AND name = p_name;
END;

