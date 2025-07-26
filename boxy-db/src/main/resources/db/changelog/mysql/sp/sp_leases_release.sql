CREATE PROCEDURE sp_leases_release(
    IN p_subscription_offset_id BIGINT,
    IN p_worker_id BIGINT)
BEGIN
    -- Instead of deleting the lease, mark it as RELEASING
    UPDATE leases 
    SET state = 'RELEASING'
    WHERE subscription_offset_id = p_subscription_offset_id AND worker_id = p_worker_id;
END;
