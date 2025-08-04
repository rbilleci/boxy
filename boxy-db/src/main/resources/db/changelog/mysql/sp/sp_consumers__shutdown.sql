CREATE PROCEDURE sp_consumers__shutdown(IN p_consumer_id VARCHAR(36))
BEGIN
    DELETE FROM consumers WHERE id = p_consumer_id;
END;

