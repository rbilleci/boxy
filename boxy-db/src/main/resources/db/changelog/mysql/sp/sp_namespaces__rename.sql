CREATE PROCEDURE sp_namespaces__rename(IN p_path VARCHAR(4000), IN p_new_name VARCHAR(500))
BEGIN
    DECLARE v_id BIGINT;
    DECLARE v_parent_id BIGINT;
    DECLARE v_parent_path VARCHAR(4000);
    DECLARE v_old_path VARCHAR(4000);
    DECLARE v_new_path VARCHAR(4000);

    -- Error handling: rollback on any error
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;

        -- Resolve the namespace and its parent, and lock the row
        SET v_id = fn_resolve_namespace_id(p_path);

        SELECT parent_id, path INTO v_parent_id, v_old_path
          FROM namespaces WHERE id = v_id FOR UPDATE;

        -- Lock the parent row if there is a parent
        IF v_parent_id IS NOT NULL THEN
            SELECT path INTO v_parent_path FROM namespaces WHERE id = v_parent_id FOR UPDATE;
        ELSE
            SET v_parent_path = NULL;
        END IF;

        -- Calculate new path (use parameter p_new_name, not v_new_name)
        IF v_parent_path IS NULL THEN
            SET v_new_path = p_new_name;
        ELSE
            SET v_new_path = CONCAT(v_parent_path, fn_resolve_namespace_delimiter(), p_new_name);
        END IF;

        -- Optional: check for name-path collision with siblings
        IF EXISTS (
            SELECT 1 FROM namespaces
             WHERE parent_id <=> v_parent_id AND name = p_new_name AND id <> v_id
        ) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Namespace with the same name already exists under this parent.';
        END IF;

        -- Lock all descendant rows to prevent concurrent updates
        DROP TEMPORARY TABLE IF EXISTS tmp_descendants;
        CREATE TEMPORARY TABLE tmp_descendants (descendant_id BIGINT PRIMARY KEY);

        INSERT INTO tmp_descendants (descendant_id)
            SELECT descendant_id FROM namespace_closures WHERE ancestor_id = v_id AND descendant_id <> v_id;

        -- Lock all descendants for update
        SELECT id FROM namespaces WHERE id IN (SELECT descendant_id FROM tmp_descendants) FOR UPDATE;

        -- Update the namespace record
        UPDATE namespaces SET name = p_new_name, path = v_new_path WHERE id = v_id;

        -- Update paths for descendants (if any)
        UPDATE namespaces n
          JOIN tmp_descendants d ON n.id = d.descendant_id
           SET n.path = CONCAT(v_new_path, SUBSTRING(n.path, CHAR_LENGTH(v_old_path) + 1));

        DROP TEMPORARY TABLE IF EXISTS tmp_descendants;

    COMMIT;
END;