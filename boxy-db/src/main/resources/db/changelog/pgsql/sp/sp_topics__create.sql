CREATE OR REPLACE FUNCTION sp_topics__create(
    IN p_path VARCHAR(4000),
    IN p_name VARCHAR(500),
    IN p_partitions INT,
    OUT v_topic_id BIGINT)
    LANGUAGE plpgsql
AS $$
DECLARE
    v_namespace_id BIGINT;
BEGIN
    v_topic_id := NULL;

    BEGIN
        -- CREATE THE TOPIC
        v_namespace_id := fn_resolve_namespace_id(p_path);
        INSERT INTO topics (namespace_id, name, partitions) VALUES (v_namespace_id, p_name, p_partitions)
        RETURNING id INTO v_topic_id;

        -- INSERT THE PARTITIONS using generate_series
        INSERT INTO partitions (topic_id, partition_number)
        SELECT v_topic_id, n FROM generate_series(0, p_partitions - 1) AS n;

    EXCEPTION WHEN OTHERS THEN
        RAISE;
    END;
END;
$$;
