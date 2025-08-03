CREATE PROCEDURE sp_namespaces__resolve_id(
    IN p_path VARCHAR(4000),
    IN p_separator VARCHAR(10),
    OUT p_id BIGINT
)
BEGIN
    SELECT id INTO p_id
      FROM namespaces
     WHERE path = p_path
     LIMIT 1;
END;
