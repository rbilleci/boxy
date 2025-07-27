CREATE PROCEDURE sp_workers_delete(IN p_worker_id BIGINT)
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; END;
    START TRANSACTION;
        DELETE FROM leases WHERE worker_id = p_worker_id;
        DELETE FROM workers WHERE id = p_worker_id;
    COMMIT;
END;