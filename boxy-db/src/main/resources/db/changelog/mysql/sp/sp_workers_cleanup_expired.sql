CREATE PROCEDURE sp_workers_cleanup_expired(
    IN p_consumer_group_id BIGINT,
    IN p_expiry_seconds INT
)
BEGIN
    -- First, identify expired workers
    CREATE TEMPORARY TABLE IF NOT EXISTS temp_expired_workers (
        worker_id BIGINT PRIMARY KEY
    );
    
    -- Find expired workers
    INSERT INTO temp_expired_workers (worker_id)
    SELECT id FROM workers 
    WHERE consumer_group_id = p_consumer_group_id 
      AND last_heartbeat < DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL p_expiry_seconds SECOND);
    
    -- Explicitly delete leases for expired workers
    DELETE FROM leases 
    WHERE worker_id IN (SELECT worker_id FROM temp_expired_workers);
    
    -- Then delete the expired workers
    DELETE FROM workers 
    WHERE id IN (SELECT worker_id FROM temp_expired_workers);
    
    -- Clean up temporary table
    DROP TEMPORARY TABLE IF EXISTS temp_expired_workers;
END;