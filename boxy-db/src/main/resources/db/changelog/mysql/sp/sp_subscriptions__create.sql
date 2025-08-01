CREATE PROCEDURE sp_subscriptions__create(
    IN p_tenant VARCHAR(255),
    IN p_name VARCHAR(255))
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN RESIGNAL; END;

    -- Insert into subscriptions table with default values for stats fields
    INSERT INTO subscriptions(tenant, name) VALUES (p_tenant,  p_name);

    -- Return the new subscription ID
    SELECT LAST_INSERT_ID() AS id;
END;
