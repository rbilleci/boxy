CREATE PROCEDURE sp_leases_cleanup_releasing(
    IN p_subscription_offset_id BIGINT,
    IN p_worker_id BIGINT)
BEGIN
    -- Delete the lease that is in RELEASING state and belongs to the specified worker
    DELETE FROM leases 
    WHERE subscription_offset_id = p_subscription_offset_id 
      AND worker_id = p_worker_id 
      AND state = 'RELEASING';
END;