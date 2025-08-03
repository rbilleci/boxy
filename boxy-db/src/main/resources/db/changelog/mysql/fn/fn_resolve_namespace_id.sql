CREATE FUNCTION fn_resolve_namespace_id(
    p_path VARCHAR(4000),
    p_separator VARCHAR(10)
)
    RETURNS BIGINT
    NOT DETERMINISTIC
    READS SQL DATA
    SQL SECURITY INVOKER
BEGIN
    DECLARE v_id BIGINT;

    WITH RECURSIVE ns AS (

      -- ANCHOR
      SELECT id, 1 AS lvl
        FROM namespaces
       WHERE parent_id IS NULL
         AND name = fn_resolve_path_segment(p_path, p_separator, 1)

      UNION ALL

      -- RECURSE
      SELECT n.id, ns.lvl + 1
        FROM namespaces n
        JOIN ns ON n.parent_id = ns.id
       WHERE n.name = fn_resolve_path_segment(p_path, p_separator, ns.lvl + 1)
    )
    SELECT id INTO v_id FROM ns ORDER BY lvl DESC LIMIT 1;

    RETURN v_id;
END;
