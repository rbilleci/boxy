CREATE PROCEDURE sp_leases_renew(
    IN p_subscription_offset_id BIGINT,
    IN p_worker_id BIGINT,
    IN p_expires_after BIGINT)
BEGIN
    UPDATE leases SET
        version = version + 1,
        expires_at = CURRENT_TIMESTAMP(3) + INTERVAL p_expires_after SECOND
    WHERE subscription_offset_id = p_subscription_offset_id AND worker_id = p_worker_id;
    SELECT ROW_COUNT() AS updated;
END;

