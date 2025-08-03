CREATE FUNCTION fn_resolve_namespace_id(p_path VARCHAR(4000))
    RETURNS BIGINT
    DETERMINISTIC
    READS SQL DATA
BEGIN
    DECLARE v_id BIGINT;
    DECLARE v_message VARCHAR(5000);

    -- EXIT HANDLER IF NAMESPACE IS NOT FOUND
    DECLARE EXIT HANDLER FOR NOT FOUND
    BEGIN
        SET v_message = CONCAT('Namespace not found: ', p_path);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_message;
    END;

    -- RETURN THE NAMESPACE (EXIT HANDLER TRIGGERED WHEN NOT FOUND)
    SELECT id INTO v_id FROM namespaces WHERE path_hash = UNHEX(MD5(p_path)) AND path = p_path LIMIT 1;
    RETURN v_id;
END;