CREATE OR REPLACE FUNCTION sp_events__publish_multi(IN p_events_json JSONB)
    LANGUAGE plpgsql
AS $$
DECLARE
    v_first_id           BIGINT;
    v_max_batch_size     BIGINT DEFAULT 1000;
BEGIN
    BEGIN
        -- Item #100: validate batch array length against configurable limit.
        SELECT COALESCE(MAX((config_value)::BIGINT), 1000) INTO v_max_batch_size
          FROM boxy_config WHERE config_key = 'max.batch.size';

        IF jsonb_array_length(p_events_json) > v_max_batch_size THEN
            RAISE EXCEPTION 'BATCH_TOO_LARGE';
        END IF;

        -- -------------------------------------------------------------------------
        -- Step 1: Expand JSON → temp table for a single-pass JOIN
        -- -------------------------------------------------------------------------
        CREATE TEMPORARY TABLE _pub_batch (
            ord  INT     NOT NULL,
            data BYTEA   NOT NULL,
            part BIGINT  NOT NULL
        );

        INSERT INTO _pub_batch (ord, data, part)
        SELECT (jt.ord)::INT,
               (jt.data)::BYTEA,
               (t.id << 16) + ((('x' || SUBSTR(MD5(jt.k), 1, 8))::BIT(32)::INT) % t.partitions) AS part
          FROM jsonb_to_recordset(p_events_json) AS jt(
               ord INT,
               path VARCHAR(4000),
               topic VARCHAR(500),
               k VARCHAR(255),
               data BYTEA)
          JOIN namespaces n ON n.path_hash = DECODE(MD5(jt.path), 'hex')
          JOIN topics     t ON t.namespace_id = n.id AND t.name = jt.topic;

        -- -------------------------------------------------------------------------
        -- Step 2: Single-transaction bulk INSERT (PostgreSQL contiguous sequence)
        -- -------------------------------------------------------------------------
        INSERT INTO events (data)
        SELECT data FROM _pub_batch ORDER BY ord
        RETURNING id INTO v_first_id;

        INSERT INTO unprocessed_events (id, partition_id)
        SELECT v_first_id + (ROW_NUMBER() OVER (ORDER BY ord) - 1),
               part
          FROM _pub_batch
         ORDER BY ord;

        DROP TABLE IF EXISTS _pub_batch;

    EXCEPTION WHEN OTHERS THEN
        DROP TABLE IF EXISTS _pub_batch;
        RAISE;
    END;
END;
$$;
