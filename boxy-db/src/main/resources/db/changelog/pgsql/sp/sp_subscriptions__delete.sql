CREATE OR REPLACE FUNCTION sp_subscriptions__delete(IN p_name VARCHAR(500))
    LANGUAGE plpgsql
AS $$
BEGIN
    BEGIN
        DELETE FROM subscriptions WHERE name = p_name;
    EXCEPTION WHEN OTHERS THEN
        RAISE;
    END;
END;
$$;
