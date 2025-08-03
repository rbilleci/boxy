CREATE PROCEDURE sp_namespaces__rename(IN p_path VARCHAR(4000), IN p_new_name VARCHAR(500))
BEGIN
    DECLARE v_id BIGINT;
    DECLARE v_parent_id BIGINT;
    DECLARE v_parent_path VARCHAR(4000);
    DECLARE v_old_path VARCHAR(4000);
    DECLARE v_new_path VARCHAR(4000);

    -- Resolve the namespace and its parent
    SET v_id = fn_resolve_namespace_id(p_path);
    SELECT parent_id INTO v_parent_id FROM namespaces WHERE id = v_id;
    IF v_parent_id IS NULL THEN
        SET v_parent_path = NULL;
    ELSE
        SELECT path INTO v_parent_path FROM namespaces WHERE id = v_parent_id;
    END IF;

    SET v_old_path = p_path;
    IF v_parent_path IS NULL THEN
        SET v_new_path = p_new_name;
    ELSE
        SET v_new_path = CONCAT(v_parent_path, '/', p_new_name);
    END IF;

    -- Update the namespace record
    UPDATE namespaces SET name = p_new_name, path = v_new_path WHERE id = v_id;

    -- Update paths for descendants
    UPDATE namespaces
       SET path = CONCAT(v_new_path, SUBSTRING(path, CHAR_LENGTH(v_old_path) + 1))
     WHERE id IN (
        SELECT descendant_id FROM namespace_closures
         WHERE ancestor_id = v_id AND descendant_id <> v_id
     );
END;
