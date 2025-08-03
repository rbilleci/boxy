CREATE PROCEDURE sp_topics__cache_get(
  IN  p_path         VARCHAR(1024),
  IN  p_topic        VARCHAR(255),
  OUT p_topic_id     BIGINT,
  OUT p_partitions   INT
)
BEGIN
  -- 1) on NOT FOUND, reset OUTs then CONTINUE to next stmt
  DECLARE v_namespace_id BIGINT;
  DECLARE CONTINUE HANDLER FOR NOT FOUND
  BEGIN
    SET p_topic_id   = NULL;
    SET p_partitions = NULL;
  END;

  -- 2) try the cache
  SELECT topic_id, partitions
    INTO p_topic_id, p_partitions
    FROM topics_cache
   WHERE path = p_path
     AND topic  = p_topic;

  IF p_topic_id IS NULL THEN
    -- cache miss: fetch the “real” value
    SELECT id INTO v_namespace_id FROM namespaces WHERE path = p_path;

    SELECT id, partitions
      INTO p_topic_id, p_partitions
      FROM topics
     WHERE namespace_id = v_namespace_id
       AND name         = p_topic;

    IF p_topic_id IS NOT NULL THEN
      INSERT INTO topics_cache (path, topic, topic_id, partitions)
        VALUES (p_path, p_topic, p_topic_id, p_partitions)
      ON DUPLICATE KEY UPDATE
        topic_id   = VALUES(topic_id),
        partitions = VALUES(partitions);
    END IF;
  END IF;
END;