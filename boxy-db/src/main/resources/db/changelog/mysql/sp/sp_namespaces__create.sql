CREATE PROCEDURE sp_namespaces__create(
  IN p_path      VARCHAR(4000),
  IN p_separator VARCHAR(10)
)
BEGIN
    DECLARE v_name        VARCHAR(500);
    DECLARE v_parent_path VARCHAR(4000);
    DECLARE v_parent_id   BIGINT;
    DECLARE v_sep_len     INT;
    DECLARE v_error       VARCHAR(1000);
    DECLARE v_id          BIGINT;

    -- VALIDATE INPUTS
    IF p_path IS NULL OR TRIM(p_path) = '' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Path cannot be null or empty';
    END IF;

    IF p_separator IS NULL OR p_separator = '' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Separator cannot be null or empty';
    END IF;

    SET v_sep_len = CHAR_LENGTH(p_separator);

  -- pull off the last segment
    SET v_name = SUBSTRING_INDEX(p_path, p_separator, -1);
        IF TRIM(v_name) = '' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid path: trailing or empty segment';
    END IF;

  -- 3) figure out the parent path & resolve its ID
    IF CHAR_LENGTH(p_path) = CHAR_LENGTH(v_name) THEN
        SET v_parent_id = NULL;
    ELSE
        SET v_parent_path = LEFT(p_path, CHAR_LENGTH(p_path) - v_sep_len - CHAR_LENGTH(v_name));
        IF TRIM(v_parent_path) = '' THEN
            SET v_parent_id = NULL;
        ELSE
            CALL sp_namespaces__resolve_id(v_parent_path, p_separator, v_parent_id);
            IF v_parent_id IS NULL THEN
                SET v_error = CONCAT('Parent path not found: ', v_parent_path);
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_error;
            END IF;
        END IF;
    END IF;

    -- INSERT
    INSERT INTO namespaces(name, parent_id) VALUES(v_name, v_parent_id);
    SET v_id = LAST_INSERT_ID();
    INSERT INTO namespace_closure(ancestor_id, descendant_id, depth)
        VALUES (v_id, v_id, 0);
    IF v_parent_id IS NOT NULL THEN
        INSERT INTO namespace_closure(ancestor_id, descendant_id, depth)
            SELECT ancestor_id, v_id, depth + 1
              FROM namespace_closure
             WHERE descendant_id = v_parent_id;
    END IF;
    SELECT v_id AS id;
END;
