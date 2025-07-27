CREATE PROCEDURE sp_consumer_groups_create(
    IN p_tenant VARCHAR(255),
    IN p_name VARCHAR(255))
BEGIN
    DECLARE v_consumer_group_id BIGINT;
    
    -- Insert into consumer_groups table
    INSERT INTO consumer_groups(tenant, name) VALUES (p_tenant, p_name);
    SET v_consumer_group_id = LAST_INSERT_ID();
    
    -- Initialize consumer_group_stats with default values
    INSERT INTO consumer_group_stats(
        consumer_group_id,
        active_workers_count,
        total_weight,
        active_partitions_count,
        last_updated
    ) VALUES (
        v_consumer_group_id,
        0,  -- active_workers_count
        0,  -- total_weight
        0,  -- active_partitions_count
        CURRENT_TIMESTAMP(3)
    );
    
    -- Return the new consumer group ID
    SELECT v_consumer_group_id AS id;
END;
