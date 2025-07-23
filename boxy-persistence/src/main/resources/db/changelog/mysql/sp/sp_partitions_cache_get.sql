CREATE PROCEDURE sp_partitions_cache_get(
  IN  p_topic_id            BIGINT,
  IN  p_partition_number    INT,
  OUT p_partition_id        BIGINT
)
BEGIN
  -- 1) on NOT FOUND, reset OUTs then CONTINUE to next stmt
  DECLARE CONTINUE HANDLER FOR NOT FOUND
  BEGIN
    SET p_partition_id   = NULL;
  END;

  -- 2) try the cache
  SELECT partition_id
    INTO p_partition_id
    FROM partitions_cache
   WHERE topic_id = p_topic_id
     AND partition_number = p_partition_number;

  IF p_partition_id IS NULL THEN
    -- cache miss: fetch the “real” value
    SELECT id
      INTO p_partition_id
      FROM partitions
   WHERE topic_id = p_topic_id
     AND partition_number  = p_partition_number;

    IF p_partition_id IS NOT NULL THEN
      INSERT INTO partitions_cache (topic_id, partition_number, partition_id)
        VALUES (p_topic_id, p_partition_number, p_partition_id)
      ON DUPLICATE KEY UPDATE
        partition_id = VALUES(partition_id);
    END IF;
  END IF;
END;