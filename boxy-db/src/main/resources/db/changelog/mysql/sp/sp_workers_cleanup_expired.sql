CREATE PROCEDURE sp_workers_cleanup_expired(
    IN p_consumer_group_id BIGINT,
    IN p_expiry_seconds INT
)
BEGIN
    -- Clean up expired workers and their leases
    -- The leases will be automatically deleted due to foreign key constraints
    DELETE FROM workers 
    WHERE consumer_group_id = p_consumer_group_id 
      AND last_heartbeat < DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL p_expiry_seconds SECOND);
END;