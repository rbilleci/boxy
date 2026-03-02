CREATE OR REPLACE FUNCTION sp_cursors__commit(
    IN p_consumer_id    VARCHAR(36),
    IN p_cursor_positions JSONB)
    LANGUAGE plpgsql
AS $$
DECLARE
    v_subscription_id BIGINT;
    v_input_count     INT DEFAULT 0;
    v_updated_count   INT DEFAULT 0;
BEGIN
    BEGIN
        -- -------------------------------------------------------------------------
        -- Validate input format
        -- -------------------------------------------------------------------------
        IF jsonb_typeof(p_cursor_positions) <> 'object' THEN
            RAISE EXCEPTION 'cursor_positions_json must be a JSON object map';
        END IF;

        -- -------------------------------------------------------------------------
        -- Resolve subscription from consumer
        -- -------------------------------------------------------------------------
        SELECT subscription_id INTO v_subscription_id
          FROM consumers
         WHERE id = p_consumer_id;

        IF v_subscription_id IS NULL THEN
            RAISE EXCEPTION 'UNKNOWN_CONSUMER';
        END IF;

        -- -------------------------------------------------------------------------
        -- Expand JSON into temp table for JOIN efficiency
        -- -------------------------------------------------------------------------
        CREATE TEMPORARY TABLE tmp_cursor_updates (
            cursor_id BIGINT PRIMARY KEY,
            position  BIGINT NOT NULL
        );

        INSERT INTO tmp_cursor_updates (cursor_id, position)
        SELECT (jt.cursor_key)::BIGINT,
               (p_cursor_positions ->> jt.cursor_key)::BIGINT
          FROM jsonb_object_keys(p_cursor_positions) jt(cursor_key);

        SELECT COUNT(*) INTO v_input_count FROM tmp_cursor_updates;

        IF v_input_count = 0 THEN
            RAISE EXCEPTION 'At least one cursor position must be provided';
        END IF;

        -- -------------------------------------------------------------------------
        -- Validate that every supplied position is a forward advance
        -- -------------------------------------------------------------------------
        SELECT COUNT(*) INTO v_updated_count
          FROM tmp_cursor_updates u
          JOIN cursors c ON c.id = u.cursor_id
                         AND c.subscription_id = v_subscription_id
         WHERE u.position > c.position;

        IF v_updated_count < v_input_count THEN
            RAISE EXCEPTION 'STALE_COMMIT';
        END IF;

        -- -------------------------------------------------------------------------
        -- Advance cursor positions
        -- -------------------------------------------------------------------------
        UPDATE cursors c
           SET position = u.position
          FROM tmp_cursor_updates u
         WHERE c.id = u.cursor_id
           AND c.subscription_id = v_subscription_id
           AND u.position > c.position;

        -- -------------------------------------------------------------------------
        -- Item #53: Release leases immediately on commit
        --
        -- NULL out locked_until for the committed cursors so other consumers can
        -- acquire these partitions without waiting for the lease to expire (~3s).
        -- Only releases leases held by p_consumer_id; does not touch leases held
        -- by other consumers (rare edge case: consumer crashed mid-processing and
        -- another consumer acquired the same cursor).
        -- -------------------------------------------------------------------------
        UPDATE consumer_leases cl
           SET locked_until = NULL
          FROM tmp_cursor_updates u
         WHERE u.cursor_id = cl.cursor_id
           AND cl.consumer_id = p_consumer_id;

        DROP TABLE IF EXISTS tmp_cursor_updates;

    EXCEPTION WHEN OTHERS THEN
        DROP TABLE IF EXISTS tmp_cursor_updates;
        RAISE;
    END;
END;
$$;
