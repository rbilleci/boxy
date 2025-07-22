CREATE PROCEDURE sp_release_lease(IN p_subscription_offset_id BIGINT, IN p_worker_id BIGINT)
BEGIN
    DELETE FROM leases WHERE subscription_offset_id = p_subscription_offset_id AND worker_id = p_worker_id;
END;
