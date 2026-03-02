CREATE OR REPLACE FUNCTION sp_events__publish_advanced(
    IN p_partition_id BIGINT,
    IN p_data BYTEA)
    LANGUAGE plpgsql
AS $$
DECLARE
    v_event_id BIGINT;
BEGIN
    BEGIN
        INSERT INTO events(data) VALUES (p_data)
        RETURNING id INTO v_event_id;

        INSERT INTO unprocessed_events(id, partition_id) VALUES (v_event_id, p_partition_id);

    EXCEPTION WHEN OTHERS THEN
        RAISE;
    END;
END;
$$;
