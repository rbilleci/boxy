CREATE PROCEDURE sp_leases_renew(
    IN p_subscription_offset_id BIGINT,
    IN p_worker_id BIGINT,
    IN p_expires_after BIGINT)
BEGIN
    -- Since we no longer use expires_at, this procedure just increments the version
    -- and ensures the lease is in ACTIVE state
    UPDATE leases SET
        version = version + 1,
        status = 'ACTIVE',
        release_started_at = NULL
    WHERE subscription_offset_id = p_subscription_offset_id 
    AND worker_id = p_worker_id;
    
    -- Also update the worker's heartbeat
    UPDATE workers SET
        last_heartbeat = CURRENT_TIMESTAMP(3)
    WHERE id = p_worker_id;
    
    SELECT ROW_COUNT() AS updated;
END;

