DROP PROCEDURE IF EXISTS sp_deregister_worker;
CREATE PROCEDURE sp_deregister_worker(IN p_id BIGINT)
BEGIN
    DELETE FROM workers WHERE id = p_id;
END;

