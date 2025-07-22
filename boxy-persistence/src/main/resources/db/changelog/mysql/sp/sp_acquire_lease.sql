CREATE PROCEDURE sp_acquire_lease(
    IN p_subscription_offset_id BIGINT,
    IN p_worker_id BIGINT,
    IN p_expires_after BIGINT)
BEGIN
    INSERT INTO leases(subscription_offset_id, worker_id, expires_at)
    VALUES (p_subscription_offset_id, p_worker_id, CURRENT_TIMESTAMP(3) + INTERVAL p_expires_after SECOND)
    ON DUPLICATE KEY UPDATE
        worker_id  = IF (leases.expires_at < CURRENT_TIMESTAMP(3), VALUES(worker_id), leases.worker_id),
        version    = IF (leases.expires_at < CURRENT_TIMESTAMP(3), leases.version + 1, leases.version),
        expires_at = IF (leases.expires_at < CURRENT_TIMESTAMP(3), VALUES(expires_at), leases.expires_at);
    SELECT ROW_COUNT() AS updated;
END;

