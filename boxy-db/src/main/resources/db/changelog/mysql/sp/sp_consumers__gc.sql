CREATE PROCEDURE sp_consumers__gc(IN p_expire_after_seconds INT)
BEGIN
    DECLARE v_timestamp TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3);
END;