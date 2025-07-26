CREATE PROCEDURE sp_leases_release(
    IN p_subscription_offset_id BIGINT,
    IN p_worker_id BIGINT)
BEGIN
    -- Mark the lease as RELEASING instead of deleting it
    UPDATE leases 
    SET status = 'RELEASING', 
        release_started_at = CURRENT_TIMESTAMP(3)
    WHERE subscription_offset_id = p_subscription_offset_id 
    AND worker_id = p_worker_id
    AND status = 'ACTIVE';
    
    SELECT ROW_COUNT() AS updated;
END;
