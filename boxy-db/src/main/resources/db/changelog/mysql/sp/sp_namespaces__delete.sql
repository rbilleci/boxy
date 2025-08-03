CREATE PROCEDURE sp_namespaces__delete(IN p_path VARCHAR(4000))
BEGIN
    DECLARE v_id BIGINT;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;

        -- Resolve ID and validate existence
        SET v_id = fn_resolve_namespace_id(p_path);
        IF v_id IS NULL THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Namespace does not exist';
        END IF;

        -- Stage descendant IDs in a temp table for locking and cascading
        DROP TEMPORARY TABLE IF EXISTS tmp_descendants;
        CREATE TEMPORARY TABLE tmp_descendants (descendant_id BIGINT PRIMARY KEY);

        INSERT INTO tmp_descendants (descendant_id)
            SELECT descendant_id FROM namespace_closures WHERE ancestor_id = v_id;

        -- Lock all affected namespaces to prevent concurrent changes
        SELECT id FROM namespaces WHERE id IN (SELECT descendant_id FROM tmp_descendants) FOR UPDATE;

        -- Delete closure links (if not handled by FK cascade)
        DELETE FROM namespace_closures WHERE descendant_id IN (SELECT descendant_id FROM tmp_descendants);

        -- Delete the namespaces themselves
        DELETE FROM namespaces WHERE id IN (SELECT descendant_id FROM tmp_descendants);

        DROP TEMPORARY TABLE IF EXISTS tmp_descendants;

    COMMIT;
END;
