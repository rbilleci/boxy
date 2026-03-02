CREATE OR REPLACE FUNCTION sp_namespaces__create(IN p_path VARCHAR(4000), OUT v_id BIGINT)
    LANGUAGE plpgsql
AS $$
DECLARE
    v_name        VARCHAR(500);
    v_parent_path VARCHAR(4000);
    v_parent_id   BIGINT;
    v_sep_len     INT;
BEGIN
    v_id := NULL;

    BEGIN
        -- VALIDATE INPUTS
        IF p_path IS NULL OR TRIM(p_path) = '' THEN
            RAISE EXCEPTION 'Path cannot be null or empty';
        END IF;

        v_sep_len := LENGTH(fn_resolve_namespace_delimiter());

        -- GET THE LAST PATH ELEMENT (THE NEW NAMESPACE)
        v_name := SUBSTRING(p_path, LENGTH(p_path) - 
                           POSITION(REVERSE(fn_resolve_namespace_delimiter()) IN REVERSE(p_path)) + 2);

        IF TRIM(v_name) = '' THEN
            RAISE EXCEPTION 'Invalid path: trailing or empty segment';
        END IF;

        -- RESOLVE THE PARENT NAMESPACE
        IF LENGTH(p_path) = LENGTH(v_name) THEN
            v_parent_id := NULL;
        ELSE
            v_parent_path := LEFT(p_path, LENGTH(p_path) - v_sep_len - LENGTH(v_name));
            v_parent_id := fn_resolve_namespace_id(v_parent_path);

            -- Ensure the parent exists
            IF v_parent_id IS NULL THEN
                RAISE EXCEPTION 'Parent namespace does not exist';
            END IF;
        END IF;

        -- Ensure the namespace doesn't already exist
        IF EXISTS (SELECT 1 FROM namespaces WHERE path = p_path) THEN
            RAISE EXCEPTION 'Namespace already exists at this path';
        END IF;

        -- INSERT THE NAMESPACE RECORD
        INSERT INTO namespaces(name, parent_id, path) VALUES(v_name, v_parent_id, p_path)
        RETURNING id INTO v_id;

        -- INSERT INTO THE CLOSURE TABLE
        INSERT INTO namespace_closures(ancestor_id, descendant_id, depth) VALUES (v_id, v_id, 0);

        IF v_parent_id IS NOT NULL THEN
            INSERT INTO namespace_closures(ancestor_id, descendant_id, depth)
                SELECT ancestor_id, v_id, depth + 1
                  FROM namespace_closures
                 WHERE descendant_id = v_parent_id;
        END IF;

    EXCEPTION WHEN OTHERS THEN
        RAISE;
    END;
END;
$$;
