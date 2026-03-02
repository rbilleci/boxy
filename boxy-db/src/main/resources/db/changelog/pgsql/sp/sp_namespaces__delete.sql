CREATE OR REPLACE FUNCTION sp_namespaces__delete(IN p_path VARCHAR(4000))
    LANGUAGE plpgsql
AS $$
DECLARE
    v_id BIGINT;
BEGIN
    BEGIN
        -- Resolve ID and validate existence
        v_id := fn_resolve_namespace_id(p_path);
        IF v_id IS NULL THEN
            RAISE EXCEPTION 'Namespace does not exist';
        END IF;

        -- Create temporary table for descendant IDs (for locking and cascading)
        CREATE TEMPORARY TABLE tmp_descendants (descendant_id BIGINT PRIMARY KEY);

        INSERT INTO tmp_descendants (descendant_id)
            SELECT descendant_id FROM namespace_closures WHERE ancestor_id = v_id;

        -- Lock all affected namespaces to prevent concurrent changes
        PERFORM id FROM namespaces WHERE id IN (SELECT descendant_id FROM tmp_descendants) FOR UPDATE;

        -- Delete closure links
        DELETE FROM namespace_closures WHERE descendant_id IN (SELECT descendant_id FROM tmp_descendants);

        -- Delete the namespaces themselves (CASCADE will handle related tables)
        DELETE FROM namespaces WHERE id IN (SELECT descendant_id FROM tmp_descendants);

        DROP TABLE IF EXISTS tmp_descendants;

    EXCEPTION WHEN OTHERS THEN
        DROP TABLE IF EXISTS tmp_descendants;
        RAISE;
    END;
END;
$$;
