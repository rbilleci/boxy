CREATE PROCEDURE sp_leases_release(
    IN p_subscription_offset_id BIGINT,
    IN p_worker_id BIGINT)
BEGIN
    -- Instead of deleting the lease, mark it as RELEASING and set the released_at timestamp
    UPDATE leases 
    SET state = 'RELEASING',
        released_at = CURRENT_TIMESTAMP(3)
    WHERE subscription_offset_id = p_subscription_offset_id AND worker_id = p_worker_id;
END;
