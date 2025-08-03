CREATE FUNCTION fn_resolve_namespace_path(p_id BIGINT, p_separator VARCHAR(10))
    RETURNS VARCHAR(4000)
    NOT DETERMINISTIC
    READS SQL DATA
    SQL SECURITY INVOKER
BEGIN
    DECLARE v_path VARCHAR(4000);

    WITH RECURSIVE ancestry AS (

        -- ANCHOR
        SELECT id, parent_id, name, name AS segment_path, 1 AS lvl
          FROM namespaces
         WHERE id = p_id

        UNION ALL

        -- RECURSE: climb up one parent at a time, prepending each name
        SELECT n.id, n.parent_id, n.name, CONCAT(n.name, '/', a.segment_path), a.lvl + 1
          FROM namespaces n
          JOIN ancestry a ON a.parent_id = n.id

  )
  SELECT segment_path INTO v_path FROM ancestry ORDER BY lvl DESC LIMIT 1;
  RETURN v_path;
END;
