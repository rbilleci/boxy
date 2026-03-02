CREATE OR REPLACE FUNCTION sp_namespaces__rename(IN p_path VARCHAR(4000), IN p_new_name VARCHAR(500))
    LANGUAGE plpgsql
AS $$
DECLARE
    v_id          BIGINT;
    v_parent_id   BIGINT;
    v_parent_path VARCHAR(4000);
    v_old_path    VARCHAR(4000);
    v_new_path    VARCHAR(4000);
BEGIN
    BEGIN
        -- Resolve the namespace and its parent, and lock the row
        v_id := fn_resolve_namespace_id(p_path);

        SELECT parent_id, path INTO v_parent_id, v_old_path
          FROM namespaces WHERE id = v_id FOR UPDATE;

        -- Lock the parent row if there is a parent
        IF v_parent_id IS NOT NULL THEN
            SELECT path INTO v_parent_path FROM namespaces WHERE id = v_parent_id FOR UPDATE;
        ELSE
            v_parent_path := NULL;
        END IF;

        -- Calculate new path
        IF v_parent_path IS NULL THEN
            v_new_path := p_new_name;
        ELSE
            v_new_path := v_parent_path || fn_resolve_namespace_delimiter() || p_new_name;
        END IF;

        -- Check for name-path collision with siblings
        IF EXISTS (
            SELECT 1 FROM namespaces
             WHERE (parent_id IS NOT DISTINCT FROM v_parent_id) 
               AND name = p_new_name 
               AND id <> v_id
        ) THEN
            RAISE EXCEPTION 'Namespace with the same name already exists under this parent';
        END IF;

        -- Create temporary table for descendant rows
        CREATE TEMPORARY TABLE tmp_descendants (descendant_id BIGINT PRIMARY KEY);

        INSERT INTO tmp_descendants (descendant_id)
            SELECT descendant_id FROM namespace_closures WHERE ancestor_id = v_id AND descendant_id <> v_id;

        -- Lock all descendants for update
        PERFORM id FROM namespaces WHERE id IN (SELECT descendant_id FROM tmp_descendants) FOR UPDATE;

        -- Update the namespace record
        UPDATE namespaces SET name = p_new_name, path = v_new_path WHERE id = v_id;

        -- Update paths for descendants (if any)
        UPDATE namespaces n
           SET path = v_new_path || SUBSTRING(n.path, LENGTH(v_old_path) + 1)
         WHERE n.id IN (SELECT descendant_id FROM tmp_descendants);

        DROP TABLE IF EXISTS tmp_descendants;

    EXCEPTION WHEN OTHERS THEN
        DROP TABLE IF EXISTS tmp_descendants;
        RAISE;
    END;
END;
$$;
