CREATE PROCEDURE sp_leases_acquire(
    IN p_subscription_offset_id BIGINT,
    IN p_worker_id BIGINT,
    IN p_expires_after BIGINT) -- This parameter is kept for backward compatibility but not used
BEGIN
    -- Get the worker's last heartbeat
    DECLARE v_worker_last_heartbeat DATETIME(3);
    
    SELECT last_heartbeat INTO v_worker_last_heartbeat
    FROM workers
    WHERE id = p_worker_id;
    
    -- Insert a new lease or update an existing one if the worker has expired
    INSERT INTO leases (subscription_offset_id, worker_id, state)
    VALUES (p_subscription_offset_id, p_worker_id, 'ACTIVE')
    ON DUPLICATE KEY UPDATE
        -- Check if the worker associated with the lease has expired (last_heartbeat is old)
        worker_id  = (SELECT IF (w.last_heartbeat < CURRENT_TIMESTAMP(3) - INTERVAL 10 SECOND, VALUES(worker_id), leases.worker_id)
                      FROM workers w WHERE w.id = leases.worker_id),
        version    = (SELECT IF (w.last_heartbeat < CURRENT_TIMESTAMP(3) - INTERVAL 10 SECOND, leases.version + 1, leases.version)
                      FROM workers w WHERE w.id = leases.worker_id),
        state      = (SELECT IF (w.last_heartbeat < CURRENT_TIMESTAMP(3) - INTERVAL 10 SECOND, 'ACTIVE', leases.state)
                      FROM workers w WHERE w.id = leases.worker_id),
        released_at = (SELECT IF (w.last_heartbeat < CURRENT_TIMESTAMP(3) - INTERVAL 10 SECOND, NULL, leases.released_at)
                      FROM workers w WHERE w.id = leases.worker_id);
    
    SELECT ROW_COUNT() AS updated;
END;

