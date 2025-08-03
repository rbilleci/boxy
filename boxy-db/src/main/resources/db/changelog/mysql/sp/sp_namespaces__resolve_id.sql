CREATE PROCEDURE sp_namespaces__resolve_id(
    IN p_path VARCHAR(4000),
    IN p_separator VARCHAR(10),
    OUT p_id BIGINT
)
BEGIN
    -- Try to find a namespace ID by path, supporting variable separator
    SELECT x.id INTO p_id
      FROM (
        SELECT
            d.id,
            REPLACE(GROUP_CONCAT(a.name ORDER BY c.depth DESC SEPARATOR '###'), '###', p_separator) AS full_path
        FROM namespace_closure c
        JOIN namespaces a ON c.ancestor_id = a.id
        JOIN namespaces d ON c.descendant_id = d.id
        GROUP BY d.id
      ) x
     WHERE x.full_path = p_path
     LIMIT 1;
END;
