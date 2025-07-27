CREATE PROCEDURE sp_consumer_groups_create(
    IN p_tenant VARCHAR(255),
    IN p_name VARCHAR(255))
BEGIN
    DECLARE v_consumer_group_id BIGINT;
    
    -- Insert into consumer_groups table with default values for stats fields
    INSERT INTO consumer_groups(
        tenant, 
        name, 
        active_workers_count, 
        total_weight, 
        active_partitions_count, 
        last_updated
    ) VALUES (
        p_tenant, 
        p_name, 
        0,  -- active_workers_count
        0,  -- total_weight
        0,  -- active_partitions_count
        CURRENT_TIMESTAMP(3)
    );
    SET v_consumer_group_id = LAST_INSERT_ID();
    
    -- Return the new consumer group ID
    SELECT v_consumer_group_id AS id;
END;
