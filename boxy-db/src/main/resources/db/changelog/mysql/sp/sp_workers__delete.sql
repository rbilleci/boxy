CREATE PROCEDURE sp_workers__delete(IN p_worker_id VARCHAR(36))
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; END;
    START TRANSACTION;
        DELETE FROM leases WHERE worker_id = p_worker_id;
        DELETE FROM workers WHERE id = p_worker_id;
    COMMIT;
END;