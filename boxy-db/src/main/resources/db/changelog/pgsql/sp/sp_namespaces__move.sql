CREATE OR REPLACE FUNCTION sp_namespaces__move(IN p_path VARCHAR(4000), IN p_new_parent_path VARCHAR(4000))
    LANGUAGE plpgsql
AS $$
DECLARE
    v_id                BIGINT;
    v_name              VARCHAR(500);
    v_old_path          VARCHAR(4000);
    v_new_parent_id     BIGINT;
    v_new_parent_path   VARCHAR(4000);
    v_new_path          VARCHAR(4000);
BEGIN
    BEGIN
        -- Resolve and lock the current namespace
        v_id := fn_resolve_namespace_id(p_path);
        SELECT name, path INTO v_name, v_old_path
          FROM namespaces WHERE id = v_id FOR UPDATE;

        -- Resolve and lock the new parent (if provided)
        IF p_new_parent_path IS NULL OR TRIM(p_new_parent_path) = '' THEN
            v_new_parent_id := NULL;
            v_new_parent_path := NULL;
            v_new_path := v_name;
        ELSE
            v_new_parent_id := fn_resolve_namespace_id(p_new_parent_path);
            IF v_new_parent_id = v_id THEN
                RAISE EXCEPTION 'Cannot move a node under itself';
            END IF;
            SELECT path INTO v_new_parent_path FROM namespaces WHERE id = v_new_parent_id FOR UPDATE;
            v_new_path := v_new_parent_path || fn_resolve_namespace_delimiter() || v_name;
        END IF;

        -- Prevent moving under a descendant (cycle)
        IF v_new_parent_id IS NOT NULL AND EXISTS (
            SELECT 1 FROM namespace_closures WHERE ancestor_id = v_id AND descendant_id = v_new_parent_id
        ) THEN
            RAISE EXCEPTION 'Cannot move a node under its own descendant';
        END IF;

        -- Create temporary table for descendant IDs and depths
        CREATE TEMPORARY TABLE tmp_descendants (
            descendant_id BIGINT PRIMARY KEY,
            depth INT
        );

        INSERT INTO tmp_descendants (descendant_id, depth)
            SELECT descendant_id, depth FROM namespace_closures WHERE ancestor_id = v_id;

        -- Lock all affected namespaces (including self and descendants)
        PERFORM id FROM namespaces WHERE id IN (SELECT descendant_id FROM tmp_descendants) FOR UPDATE;

        -- Update parent and path for the namespace being moved
        UPDATE namespaces
           SET parent_id = v_new_parent_id, path = v_new_path
         WHERE id = v_id;

        -- Update paths for descendants (excluding self)
        UPDATE namespaces n
           SET path = v_new_path || SUBSTRING(n.path, LENGTH(v_old_path) + 1)
         WHERE n.id IN (SELECT descendant_id FROM tmp_descendants) AND n.id <> v_id;

        -- Delete old closure links for moved subtree (excluding links inside the subtree itself)
        CREATE TEMPORARY TABLE tmp_descendants_list (descendant_id BIGINT PRIMARY KEY);
        INSERT INTO tmp_descendants_list (descendant_id)
          SELECT descendant_id FROM tmp_descendants;

        CREATE TEMPORARY TABLE tmp_links_to_delete (descendant_id BIGINT, ancestor_id BIGINT, PRIMARY KEY (descendant_id, ancestor_id));
        INSERT INTO tmp_links_to_delete (descendant_id, ancestor_id)
        SELECT c.descendant_id, c.ancestor_id
          FROM namespace_closures c
          JOIN tmp_descendants d ON c.descendant_id = d.descendant_id
         WHERE c.ancestor_id NOT IN (SELECT descendant_id FROM tmp_descendants_list);

        DELETE FROM namespace_closures c
         USING tmp_links_to_delete t
         WHERE c.descendant_id = t.descendant_id AND c.ancestor_id = t.ancestor_id;

        DROP TABLE IF EXISTS tmp_links_to_delete;
        DROP TABLE IF EXISTS tmp_descendants_list;

        -- Insert new closure links (if new parent is not null)
        IF v_new_parent_id IS NOT NULL THEN
            INSERT INTO namespace_closures(ancestor_id, descendant_id, depth)
            SELECT a.ancestor_id, d.descendant_id, a.depth + d.depth + 1
              FROM namespace_closures a
              CROSS JOIN tmp_descendants d
             WHERE a.descendant_id = v_new_parent_id;
        END IF;

        DROP TABLE IF EXISTS tmp_descendants;

    EXCEPTION WHEN OTHERS THEN
        DROP TABLE IF EXISTS tmp_descendants;
        DROP TABLE IF EXISTS tmp_descendants_list;
        DROP TABLE IF EXISTS tmp_links_to_delete;
        RAISE;
    END;
END;
$$;
