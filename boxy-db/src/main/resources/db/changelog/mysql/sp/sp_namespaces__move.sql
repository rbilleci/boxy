CREATE PROCEDURE sp_namespaces__move(
    IN p_path VARCHAR(4000),
    IN p_new_parent_path VARCHAR(4000),
    IN p_delimiter VARCHAR(10))
BEGIN
    DECLARE v_id BIGINT;
    DECLARE v_name VARCHAR(500);
    DECLARE v_old_path VARCHAR(4000);
    DECLARE v_new_parent_id BIGINT;
    DECLARE v_new_parent_path VARCHAR(4000);
    DECLARE v_new_path VARCHAR(4000);

    -- Error handling: rollback on any error
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;

        -- Resolve and lock the current namespace
        SET v_id = fn_resolve_namespace_id(p_path);
        SELECT name, path INTO v_name, v_old_path
          FROM namespaces WHERE id = v_id FOR UPDATE;

        -- Resolve and lock the new parent (if provided)
        IF p_new_parent_path IS NULL OR TRIM(p_new_parent_path) = '' THEN
            SET v_new_parent_id = NULL;
            SET v_new_parent_path = NULL;
            SET v_new_path = v_name;
        ELSE
            SET v_new_parent_id = fn_resolve_namespace_id(p_new_parent_path);
            IF v_new_parent_id = v_id THEN
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Cannot move a node under itself.';
            END IF;
            SELECT path INTO v_new_parent_path FROM namespaces WHERE id = v_new_parent_id FOR UPDATE;
            SET v_new_path = CONCAT(v_new_parent_path, p_delimiter, v_name);
        END IF;

        -- Prevent moving under a descendant (cycle)
        IF v_new_parent_id IS NOT NULL AND EXISTS (
            SELECT 1 FROM namespace_closures WHERE ancestor_id = v_id AND descendant_id = v_new_parent_id
        ) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Cannot move a node under its own descendant.';
        END IF;

        -- Stage descendant IDs and depths in a temp table
        DROP TEMPORARY TABLE IF EXISTS tmp_descendants;
        CREATE TEMPORARY TABLE tmp_descendants (
            descendant_id BIGINT PRIMARY KEY,
            depth INT
        );

        INSERT INTO tmp_descendants (descendant_id, depth)
            SELECT descendant_id, depth FROM namespace_closures WHERE ancestor_id = v_id;

        -- Lock all affected namespaces (including self and descendants)
        SELECT id FROM namespaces WHERE id IN (SELECT descendant_id FROM tmp_descendants) FOR UPDATE;

        -- Update parent and path for the namespace being moved
        UPDATE namespaces
           SET parent_id = v_new_parent_id, path = v_new_path
         WHERE id = v_id;

        -- Update paths for descendants (excluding self)
        UPDATE namespaces n
          JOIN tmp_descendants d ON n.id = d.descendant_id
           SET n.path = CONCAT(v_new_path, SUBSTRING(n.path, CHAR_LENGTH(v_old_path) + 1))
         WHERE n.id <> v_id;

        -- Delete old closure links for moved subtree (excluding links inside the subtree itself)
        -- Avoid "Can't reopen table" by using a helper table for the NOT IN list

        DROP TEMPORARY TABLE IF EXISTS tmp_descendants_list;
        CREATE TEMPORARY TABLE tmp_descendants_list (descendant_id BIGINT PRIMARY KEY);

        INSERT INTO tmp_descendants_list (descendant_id)
          SELECT descendant_id FROM tmp_descendants;

        DROP TEMPORARY TABLE IF EXISTS tmp_links_to_delete;
        CREATE TEMPORARY TABLE tmp_links_to_delete (descendant_id BIGINT, ancestor_id BIGINT, PRIMARY KEY (descendant_id, ancestor_id));

        INSERT INTO tmp_links_to_delete (descendant_id, ancestor_id)
        SELECT c.descendant_id, c.ancestor_id
          FROM namespace_closures c
          JOIN tmp_descendants d ON c.descendant_id = d.descendant_id
         WHERE c.ancestor_id NOT IN (SELECT descendant_id FROM tmp_descendants_list);

        DELETE c FROM namespace_closures c
          JOIN tmp_links_to_delete t
            ON c.descendant_id = t.descendant_id AND c.ancestor_id = t.ancestor_id;

        DROP TEMPORARY TABLE IF EXISTS tmp_links_to_delete;
        DROP TEMPORARY TABLE IF EXISTS tmp_descendants_list;

        -- Insert new closure links (if new parent is not null)
        IF v_new_parent_id IS NOT NULL THEN
            INSERT INTO namespace_closures(ancestor_id, descendant_id, depth)
            SELECT a.ancestor_id, d.descendant_id, a.depth + d.depth + 1
              FROM namespace_closures a
              JOIN tmp_descendants d ON 1=1
             WHERE a.descendant_id = v_new_parent_id;
        END IF;

        DROP TEMPORARY TABLE IF EXISTS tmp_descendants;

    COMMIT;
END;