CREATE OR REPLACE FUNCTION sp_consumers__deregister(IN p_consumer_id VARCHAR(36))
    LANGUAGE plpgsql
AS $$
BEGIN
    BEGIN
        DELETE FROM consumers WHERE id = p_consumer_id;
    EXCEPTION WHEN OTHERS THEN
        RAISE;
    END;
END;
$$;
