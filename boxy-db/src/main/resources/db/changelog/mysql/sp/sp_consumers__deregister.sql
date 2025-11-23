CREATE PROCEDURE sp_consumers__deregister(IN p_consumer_id VARCHAR(36))
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; END;
    START TRANSACTION;
        UPDATE leases SET consumer_id = NULL, locked_until = NULL WHERE consumer_id = p_consumer_id;
        DELETE FROM consumers WHERE id = p_consumer_id;
    COMMIT;
END;
