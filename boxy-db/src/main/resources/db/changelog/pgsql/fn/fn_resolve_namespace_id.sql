CREATE OR REPLACE FUNCTION fn_resolve_namespace_id(p_path VARCHAR(4000))
    RETURNS BIGINT
    LANGUAGE plpgsql
    STABLE
    RETURNS NULL ON NULL INPUT
AS $$
DECLARE
    v_id BIGINT;
BEGIN
    SELECT id INTO v_id
      FROM namespaces
     WHERE path_hash = DECODE(MD5(p_path), 'hex')
       AND path = p_path
     LIMIT 1;

    RETURN v_id;
END;
$$;
