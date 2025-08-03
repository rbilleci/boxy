CREATE PROCEDURE sp_topics__cache_get(
  IN  p_path         VARCHAR(4000),
  IN  p_topic        VARCHAR(500),
  OUT p_topic_id     BIGINT,
  OUT p_partitions   INT
)
BEGIN
  -- 1) on NOT FOUND, reset OUTs then CONTINUE to next stmt
  DECLARE v_namespace_id BIGINT;
  DECLARE v_path_hash BINARY(16);
  DECLARE CONTINUE HANDLER FOR NOT FOUND
  BEGIN
    SET p_topic_id   = NULL;
    SET p_partitions = NULL;
  END;

  SET v_path_hash = UNHEX(MD5(p_path));

  -- 2) try the cache
  SELECT topic_id, partitions
    INTO p_topic_id, p_partitions
    FROM topics_cache
   WHERE path_hash = v_path_hash
     AND topic     = p_topic
     AND path      = p_path;

  IF p_topic_id IS NULL THEN
    -- cache miss: fetch the "real" value
    SET v_namespace_id = fn_resolve_namespace_id(p_path, '/');

    SELECT id, partitions
      INTO p_topic_id, p_partitions
      FROM topics
     WHERE namespace_id = v_namespace_id
       AND name         = p_topic;

    IF p_topic_id IS NOT NULL THEN
      INSERT INTO topics_cache (path_hash, path, topic, topic_id, partitions)
        VALUES (v_path_hash, p_path, p_topic, p_topic_id, p_partitions)
      ON DUPLICATE KEY UPDATE
        path       = VALUES(path),
        topic_id   = VALUES(topic_id),
        partitions = VALUES(partitions);
    END IF;
  END IF;
END;