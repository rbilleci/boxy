CREATE PROCEDURE sp_consumers__gc()
BEGIN
    DECLARE v_timestamp TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3);

    START TRANSACTION;

    DELETE
      FROM consumers
     WHERE heartbeat_deadline <= v_timestamp;

    COMMIT;
END;
