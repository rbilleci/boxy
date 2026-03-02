CREATE OR REPLACE FUNCTION sp_consumers__gc()
    LANGUAGE plpgsql
AS $$
DECLARE
    v_timestamp  TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3);
    v_batch_size INT DEFAULT 100;
    v_deleted    INT DEFAULT 0;
BEGIN
    BEGIN
        -- -------------------------------------------------------------------------
        -- Step 1: Item #60 — explicit batch lease cleanup (avoids large CASCADE)
        --
        -- Remove consumer_leases rows for all expired consumers in v_batch_size
        -- chunks.  Each iteration is its own transaction to bound lock hold time.
        -- The JOIN filters only leases belonging to consumers that are about to
        -- be GC'd, leaving active consumers' leases untouched.
        -- -------------------------------------------------------------------------
        LOOP
            DELETE FROM consumer_leases cl
              USING consumers c
             WHERE c.id = cl.consumer_id
               AND c.heartbeat_deadline <= v_timestamp
             LIMIT v_batch_size;

            GET DIAGNOSTICS v_deleted = ROW_COUNT;

            IF v_deleted = 0 THEN
                EXIT;
            END IF;
        END LOOP;

        -- -------------------------------------------------------------------------
        -- Step 2: Item #59 — batch consumer deletion with LIMIT
        --
        -- After Step 1, the consumer_leases table has no rows for expired consumers;
        -- the CASCADE triggered by each DELETE here does no work.  Batching keeps
        -- each transaction small and bounds row-lock hold time.
        -- -------------------------------------------------------------------------
        LOOP
            DELETE FROM consumers
             WHERE heartbeat_deadline <= v_timestamp
             LIMIT v_batch_size;

            GET DIAGNOSTICS v_deleted = ROW_COUNT;

            IF v_deleted = 0 THEN
                EXIT;
            END IF;
        END LOOP;

    EXCEPTION WHEN OTHERS THEN
        RAISE;
    END;
END;
$$;
