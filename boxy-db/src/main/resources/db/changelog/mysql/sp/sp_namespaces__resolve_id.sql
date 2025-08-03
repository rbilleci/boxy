CREATE PROCEDURE sp_namespaces__resolve_id(
    IN p_path VARCHAR(4000),
    IN p_separator VARCHAR(10),
    OUT p_id BIGINT
)
BEGIN
    SELECT d.id INTO p_id
      FROM namespace_closure c
      JOIN namespaces a ON c.ancestor_id = a.id
      JOIN namespaces d ON c.descendant_id = d.id
     GROUP BY d.id
    HAVING GROUP_CONCAT(a.name ORDER BY c.depth DESC SEPARATOR p_separator) = p_path;
END;
