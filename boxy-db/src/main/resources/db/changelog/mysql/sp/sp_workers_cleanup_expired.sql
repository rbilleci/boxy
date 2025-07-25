CREATE PROCEDURE sp_workers_cleanup_expired(
    IN p_heartbeat_timeout_seconds INT
)
BEGIN
    -- Find workers with expired heartbeats
    DECLARE expired_cutoff DATETIME(3);
    SET expired_cutoff = DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL p_heartbeat_timeout_seconds SECOND);
    
    -- Delete leases for workers with expired heartbeats
    -- This will cascade delete the leases due to the foreign key constraint
    DELETE FROM leases 
    WHERE worker_id IN (
        SELECT id FROM workers 
        WHERE last_heartbeat < expired_cutoff
    );
    
    -- Delete workers with expired heartbeats
    DELETE FROM workers 
    WHERE last_heartbeat < expired_cutoff;
    
    -- Return the number of workers deleted
    SELECT ROW_COUNT() AS workers_deleted;
END;