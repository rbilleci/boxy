CREATE PROCEDURE sp_workers_shutdown(
    IN p_id BIGINT)
BEGIN
    DELETE FROM workers WHERE id = p_id;
END;

