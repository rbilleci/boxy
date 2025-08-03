-- FUNCTION TO BUILD THE PATH OF A NAMESPACE GIVEN ITS ID
CREATE FUNCTION fn_namespace_path(p_id BIGINT)
RETURNS VARCHAR(1024)
DETERMINISTIC
BEGIN
    DECLARE v_name VARCHAR(255);
    DECLARE v_parent BIGINT;
    DECLARE v_path VARCHAR(1024) DEFAULT '';
    DECLARE v_id BIGINT;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_id = NULL;

    SET v_id = p_id;

    WHILE v_id IS NOT NULL DO
        SELECT name, parent_id INTO v_name, v_parent FROM namespaces WHERE id = v_id;
        IF v_name IS NULL THEN
            RETURN NULL;
        END IF;
        IF v_path = '' THEN
            SET v_path = v_name;
        ELSE
            SET v_path = CONCAT(v_name, '/', v_path);
        END IF;
        SET v_id = v_parent;
    END WHILE;

    RETURN v_path;
END;
