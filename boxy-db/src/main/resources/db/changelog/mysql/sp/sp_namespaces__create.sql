CREATE PROCEDURE sp_namespaces__create(IN p_path VARCHAR(4000), IN p_delimiter VARCHAR(10))
BEGIN
    DECLARE v_name        VARCHAR(500);
    DECLARE v_parent_path VARCHAR(4000);
    DECLARE v_parent_id   BIGINT;
    DECLARE v_sep_len     INT;
    DECLARE v_id          BIGINT;

    -- Rollback on any error
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;

        -- VALIDATE INPUTS
        IF p_path IS NULL OR TRIM(p_path) = '' THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Path cannot be null or empty';
        END IF;

        IF p_delimiter IS NULL OR p_delimiter = '' THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Delimiter cannot be null or empty';
        END IF;

        SET v_sep_len = CHAR_LENGTH(p_delimiter);

        -- GET THE LAST PATH ELEMENT (THE NEW NAMESPACE)
        SET v_name = SUBSTRING_INDEX(p_path, p_delimiter, -1);
        IF TRIM(v_name) = '' THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid path: trailing or empty segment';
        END IF;

        -- RESOLVE THE PARENT NAMESPACE
        IF CHAR_LENGTH(p_path) = CHAR_LENGTH(v_name) THEN
            SET v_parent_id = NULL;
        ELSE
            SET v_parent_path = LEFT(p_path, CHAR_LENGTH(p_path) - v_sep_len - CHAR_LENGTH(v_name));
            SET v_parent_id = fn_resolve_namespace_id(v_parent_path);

            -- Ensure the parent exists
            IF v_parent_id IS NULL THEN
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Parent namespace does not exist';
            END IF;
        END IF;

        -- Ensure the namespace doesn't already exist
        IF EXISTS (SELECT 1 FROM namespaces WHERE path = p_path) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Namespace already exists at this path';
        END IF;

        -- INSERT THE NAMESPACE RECORD
        INSERT INTO namespaces(name, parent_id, path) VALUES(v_name, v_parent_id, p_path);
        SET v_id = LAST_INSERT_ID();

        -- INSERT INTO THE CLOSURE TABLE
        INSERT INTO namespace_closures(ancestor_id, descendant_id, depth) VALUES (v_id, v_id, 0);

        IF v_parent_id IS NOT NULL THEN
            INSERT INTO namespace_closures(ancestor_id, descendant_id, depth)
                SELECT ancestor_id, v_id, depth + 1
                  FROM namespace_closures
                 WHERE descendant_id = v_parent_id;
        END IF;

    COMMIT;

    SELECT v_id AS id;
END;
