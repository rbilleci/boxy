CREATE PROCEDURE sp_consumers__delete(IN p_consumer_id VARCHAR(36))
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; END;
    START TRANSACTION;
        DELETE FROM leases WHERE consumer_id = p_consumer_id;
        DELETE FROM consumers WHERE id = p_consumer_id;
    COMMIT;
END;