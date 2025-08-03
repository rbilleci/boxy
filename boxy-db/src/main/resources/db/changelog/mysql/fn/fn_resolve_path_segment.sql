CREATE FUNCTION fn_resolve_path_segment(
  in_path TEXT,
  in_sep  VARCHAR(10),
  in_pos  INT
) RETURNS VARCHAR(500)
  DETERMINISTIC
  NO SQL
  RETURN SUBSTRING_INDEX(SUBSTRING_INDEX(in_path, in_sep, in_pos), in_sep, -1);