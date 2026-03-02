CREATE OR REPLACE FUNCTION sp_topics__delete(IN p_path VARCHAR(4000), IN p_name VARCHAR(500))
    LANGUAGE plpgsql
AS $$
BEGIN
    BEGIN
        DELETE FROM topics WHERE namespace_id = fn_resolve_namespace_id(p_path) AND name = p_name;
    EXCEPTION WHEN OTHERS THEN
        RAISE;
    END;
END;
$$;
