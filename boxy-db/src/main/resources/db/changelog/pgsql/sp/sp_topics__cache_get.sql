CREATE OR REPLACE FUNCTION sp_topics__cache_get(
  IN  p_path         VARCHAR(4000),
  IN  p_topic        VARCHAR(500),
  OUT p_topic_id     BIGINT,
  OUT p_partitions   INT)
    LANGUAGE plpgsql
AS $$
DECLARE
    v_path_hash BYTEA;
BEGIN
    v_path_hash := DECODE(MD5(p_path), 'hex');

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
        ON CONFLICT (path_hash, topic) DO UPDATE
            SET path       = EXCLUDED.path,
                topic_id   = EXCLUDED.topic_id,
                partitions = EXCLUDED.partitions;

        SELECT topic_id, partitions INTO p_topic_id, p_partitions
          FROM topics_cache
         WHERE path_hash = v_path_hash AND topic = p_topic AND path = p_path
         LIMIT 1;
    END IF;
END;
$$;
