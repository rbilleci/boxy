CREATE OR REPLACE FUNCTION fn_resolve_partition_id(p_topic_id BIGINT, p_partition_number INT)
    RETURNS BIGINT
    LANGUAGE plpgsql
    IMMUTABLE
    RETURNS NULL ON NULL INPUT
AS $$
BEGIN
    IF p_partition_number < 0 OR p_partition_number >= 65536 THEN
        RAISE EXCEPTION 'The partition number must be from [0, 65536)';
    END IF;
    -- Bitwise left shift by 16, then add the partition number
    RETURN (p_topic_id << 16) + p_partition_number;
END;
$$;
