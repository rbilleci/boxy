CREATE PROCEDURE sp_topics__cache_get(
  IN  p_path         VARCHAR(4000),
  IN  p_topic        VARCHAR(500),
  OUT p_topic_id     BIGINT,
  OUT p_partitions   INT
)
BEGIN
    DECLARE v_path_hash BINARY(16);
    SET v_path_hash = UNHEX(MD5(p_path));

    -- CHECK CACHE
    SELECT topic_id, partitions INTO p_topic_id, p_partitions
      FROM topics_cache
     WHERE path_hash = v_path_hash AND topic = p_topic AND path = p_path;

    -- CACHE MISS, ADD ENTRY
    IF p_topic_id IS NULL THEN

        INSERT INTO topics_cache (path_hash, path, topic, topic_id, partitions)
        SELECT v_path_hash, p_path, p_topic, t.id, t.partitions
          FROM topics AS t
         WHERE t.namespace_id = fn_resolve_namespace_id(p_path)
           AND t.name = p_topic
         LIMIT 1
        ON DUPLICATE KEY UPDATE
            path       = VALUES(path),
            topic_id   = VALUES(topic_id),
            partitions = VALUES(partitions);

      SELECT topic_id, partitions INTO p_topic_id, p_partitions
        FROM topics_cache
       WHERE path_hash = v_path_hash AND topic = p_topic AND path = p_path
       LIMIT 1;

  END IF;
END;