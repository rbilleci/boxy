CREATE PROCEDURE sp_leases_renew(
    IN p_subscription_offset_id BIGINT,
    IN p_worker_id BIGINT,
    IN p_expires_after BIGINT) -- This parameter is kept for backward compatibility but not used
BEGIN
    -- Update the lease version to indicate it's still active
    -- We don't need to update any expiration time since we rely on worker's last_heartbeat
    UPDATE leases SET
        version = version + 1
    WHERE subscription_offset_id = p_subscription_offset_id 
      AND worker_id = p_worker_id
      AND state = 'ACTIVE';
    
    SELECT ROW_COUNT() AS updated;
END;

