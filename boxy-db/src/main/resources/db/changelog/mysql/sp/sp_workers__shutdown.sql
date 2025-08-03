CREATE PROCEDURE sp_workers__shutdown(IN p_worker_id VARCHAR(36))
BEGIN
    DELETE FROM workers WHERE id = p_id;
END;

