CREATE PROCEDURE sp_namespaces__resolve_id(
    IN p_path VARCHAR(4000),
    IN p_separator VARCHAR(10),
    OUT p_id BIGINT
)
BEGIN
    DECLARE v_path_hash BINARY(16);
    SET v_path_hash = UNHEX(MD5(p_path));
    SELECT id INTO p_id
      FROM namespaces
     WHERE path_hash = v_path_hash
       AND path      = p_path
     LIMIT 1;
END;
