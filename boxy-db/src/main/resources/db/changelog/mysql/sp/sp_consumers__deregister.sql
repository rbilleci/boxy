CREATE PROCEDURE sp_consumers__deregister(IN p_session_id VARCHAR(36))
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; END;
    START TRANSACTION;
        UPDATE leases SET consumer_id = NULL, locked_until = NULL WHERE consumer_id = p_session_id;
        DELETE FROM consumers WHERE id = p_session_id;
    COMMIT;
END;
