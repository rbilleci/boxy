CREATE PROCEDURE sp_topics__cache_get(
  IN  p_namespace_id BIGINT,
  IN  p_topic        VARCHAR(255),
  OUT p_topic_id     BIGINT,
  OUT p_partitions   INT
)
BEGIN
  -- 1) on NOT FOUND, reset OUTs then CONTINUE to next stmt
  DECLARE CONTINUE HANDLER FOR NOT FOUND
  BEGIN
    SET p_topic_id   = NULL;
    SET p_partitions = NULL;
  END;

  -- 2) try the cache
  SELECT topic_id, partitions
    INTO p_topic_id, p_partitions
    FROM topics_cache
   WHERE namespace_id = p_namespace_id
     AND topic  = p_topic;

  IF p_topic_id IS NULL THEN
    -- cache miss: fetch the “real” value
    SELECT id, partitions
      INTO p_topic_id, p_partitions
      FROM topics
     WHERE namespace_id = p_namespace_id
       AND name   = p_topic;

    IF p_topic_id IS NOT NULL THEN
      INSERT INTO topics_cache (namespace_id, topic, topic_id, partitions)
        VALUES (p_namespace_id, p_topic, p_topic_id, p_partitions)
      ON DUPLICATE KEY UPDATE
        topic_id   = VALUES(topic_id),
        partitions = VALUES(partitions);
    END IF;
  END IF;
END;