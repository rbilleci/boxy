CREATE FUNCTION fn_resolve_topic_id(p_topic_path VARCHAR(4510))
    RETURNS BIGINT
    DETERMINISTIC
    READS SQL DATA
BEGIN
    DECLARE v_delimiter VARCHAR(10);
    DECLARE v_delimiter_length INT;
    DECLARE v_topic_name VARCHAR(500);
    DECLARE v_namespace_path VARCHAR(4000);
    DECLARE v_namespace_id BIGINT;
    DECLARE v_topic_id BIGINT;

    DECLARE EXIT HANDLER FOR NOT FOUND
    BEGIN
        RETURN NULL;
    END;

    SET v_delimiter = fn_resolve_namespace_delimiter();
    SET v_delimiter_length = CHAR_LENGTH(v_delimiter);

    IF p_topic_path IS NULL OR TRIM(p_topic_path) = '' THEN
        RETURN NULL;
    END IF;

    SET v_topic_name = SUBSTRING_INDEX(p_topic_path, v_delimiter, -1);

    IF v_topic_name IS NULL OR TRIM(v_topic_name) = '' THEN
        RETURN NULL;
    END IF;

    IF p_topic_path NOT LIKE CONCAT('%', v_delimiter, '%') THEN
        RETURN NULL;
    END IF;

    SET v_namespace_path = LEFT(p_topic_path, CHAR_LENGTH(p_topic_path) - CHAR_LENGTH(v_topic_name) - v_delimiter_length);

    IF v_namespace_path IS NULL OR TRIM(v_namespace_path) = '' THEN
        RETURN NULL;
    END IF;

    SET v_namespace_id = fn_resolve_namespace_id(v_namespace_path);

    IF v_namespace_id IS NULL THEN
        RETURN NULL;
    END IF;

    SELECT id INTO v_topic_id FROM topics WHERE namespace_id = v_namespace_id AND name = v_topic_name LIMIT 1;

    RETURN v_topic_id;
END;
