CREATE PROCEDURE sp_workers_deregister(
    IN p_id BIGINT)
BEGIN
    -- Delete leases held by this worker
    -- This will cascade delete the leases due to the foreign key constraint
    DELETE FROM leases WHERE worker_id = p_id;
    
    -- Delete the worker
    DELETE FROM workers WHERE id = p_id;
    
    -- Return the number of workers deleted
    SELECT ROW_COUNT() AS workers_deleted;
END;

