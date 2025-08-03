CREATE PROCEDURE sp_namespaces__move(IN p_path VARCHAR(4000), IN p_new_parent_path VARCHAR(4000))
BEGIN
    DECLARE v_id BIGINT;
    DECLARE v_name VARCHAR(500);
    DECLARE v_old_path VARCHAR(4000);
    DECLARE v_new_parent_id BIGINT;
    DECLARE v_new_parent_path VARCHAR(4000);
    DECLARE v_new_path VARCHAR(4000);

    -- Resolve current namespace
    SET v_id = fn_resolve_namespace_id(p_path);
    SELECT name, path INTO v_name, v_old_path FROM namespaces WHERE id = v_id;

    -- Resolve new parent (if provided)
    IF p_new_parent_path IS NULL OR TRIM(p_new_parent_path) = '' THEN
        SET v_new_parent_id = NULL;
        SET v_new_parent_path = NULL;
        SET v_new_path = v_name;
    ELSE
        SET v_new_parent_id = fn_resolve_namespace_id(p_new_parent_path);
        SELECT path INTO v_new_parent_path FROM namespaces WHERE id = v_new_parent_id;
        SET v_new_path = CONCAT(v_new_parent_path, '/', v_name);
    END IF;

    -- Update parent and path for the namespace
    UPDATE namespaces SET parent_id = v_new_parent_id, path = v_new_path WHERE id = v_id;

    -- Update paths for descendants
    UPDATE namespaces
       SET path = CONCAT(v_new_path, SUBSTRING(path, CHAR_LENGTH(v_old_path) + 1))
     WHERE id IN (
        SELECT descendant_id FROM namespace_closures
         WHERE ancestor_id = v_id AND descendant_id <> v_id
     );

    -- Update closure table: remove old ancestor links
    DELETE FROM namespace_closures
     WHERE descendant_id IN (
        SELECT descendant_id FROM namespace_closures WHERE ancestor_id = v_id
     )
       AND ancestor_id NOT IN (
        SELECT descendant_id FROM namespace_closures WHERE ancestor_id = v_id
     );

    -- Insert new ancestor links
    IF v_new_parent_id IS NOT NULL THEN
        INSERT INTO namespace_closures(ancestor_id, descendant_id, depth)
        SELECT a.ancestor_id, d.descendant_id, a.depth + d.depth + 1
          FROM namespace_closures a, namespace_closures d
         WHERE a.descendant_id = v_new_parent_id
           AND d.ancestor_id = v_id;
    END IF;
END;
