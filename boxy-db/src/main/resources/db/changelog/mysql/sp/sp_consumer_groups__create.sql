CREATE PROCEDURE sp_consumer_groups__create(
    IN p_tenant VARCHAR(255),
    IN p_name VARCHAR(255))
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN RESIGNAL; END;

    -- Insert into consumer_groups table with default values for stats fields
    INSERT INTO consumer_groups(tenant, name) VALUES (p_tenant,  p_name);

    -- Return the new consumer group ID
    SELECT LAST_INSERT_ID() AS id;
END;
