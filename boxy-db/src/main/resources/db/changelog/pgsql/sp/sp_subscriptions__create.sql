CREATE OR REPLACE FUNCTION sp_subscriptions__create(IN p_name VARCHAR(500), OUT v_id BIGINT)
    LANGUAGE plpgsql
AS $$
BEGIN
    v_id := NULL;

    BEGIN
        INSERT INTO subscriptions(name) VALUES (p_name)
        RETURNING id INTO v_id;
    EXCEPTION WHEN OTHERS THEN
        RAISE;
    END;
END;
$$;
