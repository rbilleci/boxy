-- FUNCTION TO RESOLVE A NAMESPACE ID FROM ITS PATH
CREATE FUNCTION fn_resolve_namespace_id(p_path VARCHAR(4000))
    RETURNS BIGINT
    NOT DETERMINISTIC
    SQL SECURITY INVOKER
BEGIN
    DECLARE v_parent_id BIGINT;
    DECLARE v_id BIGINT;
    DECLARE v_segment VARCHAR(500);
    DECLARE v_pos INT DEFAULT 1;
    DECLARE v_next INT;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_id = NULL;

    IF p_path IS NULL OR p_path = '' THEN
        RETURN NULL;
    END IF;

    SET v_parent_id = NULL;
    SET v_next = LOCATE('/', p_path, v_pos);

    WHILE v_next > 0 DO
        SET v_segment = SUBSTRING(p_path, v_pos, v_next - v_pos);
        SELECT id INTO v_id FROM namespaces WHERE parent_id <=> v_parent_id AND name = v_segment;
        IF v_id IS NULL THEN
            RETURN NULL;
        END IF;
        SET v_parent_id = v_id;
        SET v_pos = v_next + 1;
        SET v_next = LOCATE('/', p_path, v_pos);
    END WHILE;

    SET v_segment = SUBSTRING(p_path, v_pos);
    SELECT id INTO v_id FROM namespaces WHERE parent_id <=> v_parent_id AND name = v_segment;
    RETURN v_id;
END;
