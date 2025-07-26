CREATE PROCEDURE sp_leases_acquire(
    IN p_subscription_offset_id BIGINT,
    IN p_worker_id BIGINT,
    IN p_expires_after BIGINT)
BEGIN
    DECLARE v_worker_active BOOLEAN;
    
    -- Check if the worker is active
    SELECT COUNT(*) > 0 INTO v_worker_active
    FROM workers
    WHERE id = p_worker_id
    AND last_heartbeat >= DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL p_expires_after SECOND);
    
    -- Only proceed if the worker is active
    IF v_worker_active THEN
        INSERT INTO leases (subscription_offset_id, worker_id, status)
        VALUES (p_subscription_offset_id, p_worker_id, 'ACTIVE')
        ON DUPLICATE KEY UPDATE
            worker_id = IF (
                -- Check if the current lease owner is still active
                NOT EXISTS (
                    SELECT 1 FROM workers w
                    WHERE w.id = leases.worker_id
                    AND w.last_heartbeat >= DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL p_expires_after SECOND)
                ),
                VALUES(worker_id),
                leases.worker_id
            ),
            version = IF (
                NOT EXISTS (
                    SELECT 1 FROM workers w
                    WHERE w.id = leases.worker_id
                    AND w.last_heartbeat >= DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL p_expires_after SECOND)
                ),
                leases.version + 1,
                leases.version
            ),
            status = IF (
                NOT EXISTS (
                    SELECT 1 FROM workers w
                    WHERE w.id = leases.worker_id
                    AND w.last_heartbeat >= DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL p_expires_after SECOND)
                ),
                'ACTIVE',
                leases.status
            ),
            release_started_at = IF (
                NOT EXISTS (
                    SELECT 1 FROM workers w
                    WHERE w.id = leases.worker_id
                    AND w.last_heartbeat >= DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL p_expires_after SECOND)
                ),
                NULL,
                leases.release_started_at
            );
    END IF;
    
    SELECT ROW_COUNT() AS updated;
END;

