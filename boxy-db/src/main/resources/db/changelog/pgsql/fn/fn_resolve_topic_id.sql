CREATE OR REPLACE FUNCTION fn_resolve_topic_id(p_topic_path VARCHAR(4510))
    RETURNS BIGINT
    LANGUAGE plpgsql
    STABLE
    RETURNS NULL ON NULL INPUT
AS $$
DECLARE
    v_delimiter         VARCHAR(10);
    v_delimiter_length  INT;
    v_topic_name        VARCHAR(500);
    v_namespace_path    VARCHAR(4000);
    v_namespace_id      BIGINT;
    v_topic_id          BIGINT;
BEGIN
    v_delimiter := fn_resolve_namespace_delimiter();
    v_delimiter_length := LENGTH(v_delimiter);

    IF p_topic_path IS NULL OR TRIM(p_topic_path) = '' THEN
        RETURN NULL;
    END IF;

    -- Extract the last segment (topic name)
    v_topic_name := SUBSTRING(p_topic_path, LENGTH(p_topic_path) - 
                              POSITION(REVERSE(v_delimiter) IN REVERSE(p_topic_path)) + 2);

    IF v_topic_name IS NULL OR TRIM(v_topic_name) = '' THEN
        RETURN NULL;
    END IF;

    -- Check if delimiter exists in path
    IF p_topic_path NOT LIKE '%' || v_delimiter || '%' THEN
        RETURN NULL;
    END IF;

    -- Extract namespace path (everything except last topic segment)
    v_namespace_path := LEFT(p_topic_path, LENGTH(p_topic_path) - LENGTH(v_topic_name) - v_delimiter_length);

    IF v_namespace_path IS NULL OR TRIM(v_namespace_path) = '' THEN
        RETURN NULL;
    END IF;

    -- Resolve namespace ID
    v_namespace_id := fn_resolve_namespace_id(v_namespace_path);

    IF v_namespace_id IS NULL THEN
        RETURN NULL;
    END IF;

    -- Resolve topic ID
    SELECT id INTO v_topic_id
      FROM topics
     WHERE namespace_id = v_namespace_id AND name = v_topic_name
     LIMIT 1;

    RETURN v_topic_id;
END;
$$;
