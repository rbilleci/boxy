-- sp_events__publish_multi (v4 — batch size validation)
--
-- v2: replaced N-loop with single-transaction bulk INSERT (changeset 2)
-- v3: adds EXIT HANDLER with ROLLBACK and temp table cleanup
-- v4: adds maximum batch size validation from boxy_config (item #100)
--
-- === Item #100: Add maximum array length validation ===
--   Validates that the batch does not exceed the configured maximum array length.
--   The limit is read from boxy_config ('max.batch.size'), defaulting
--   to 1000 if not configured.
--
--   Override at runtime:
--     UPDATE boxy_config SET config_value = '500'
--      WHERE config_key = 'max.batch.size';
--
--   SIGNAL SQLSTATE '45000' MESSAGE 'BATCH_TOO_LARGE' is raised if the
--   batch exceeds the limit, allowing callers to distinguish this from
--   infrastructure failures via DataAccessException.hasErrorCode().
DROP PROCEDURE IF EXISTS sp_events__publish_multi;
CREATE PROCEDURE sp_events__publish_multi(IN p_events_json JSON)
BEGIN
    DECLARE v_first_id           BIGINT;
    DECLARE v_max_batch_size     BIGINT DEFAULT 1000;

    -- EXIT HANDLER: on any SQL error, roll back the bulk INSERT transaction,
    -- drop the temp table (if it was created before the error), and re-signal.
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        DROP TEMPORARY TABLE IF EXISTS _pub_batch;
        RESIGNAL;
    END;

    -- Item #100: validate batch array length against configurable limit.
    SELECT COALESCE(MAX(CAST(config_value AS SIGNED)), 1000) INTO v_max_batch_size
      FROM boxy_config WHERE config_key = 'max.batch.size';

    IF JSON_LENGTH(p_events_json) > v_max_batch_size THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'BATCH_TOO_LARGE';
    END IF;

    -- -------------------------------------------------------------------------
    -- Step 1: Expand JSON → temp table for a single-pass JOIN
    -- -------------------------------------------------------------------------
    DROP TEMPORARY TABLE IF EXISTS _pub_batch;
    CREATE TEMPORARY TABLE _pub_batch (
        ord  INT     NOT NULL,
        data LONGBLOB NOT NULL,
        part BIGINT  NOT NULL
    ) ENGINE=InnoDB;

    INSERT INTO _pub_batch (ord, data, part)
    SELECT jt.ord,
           jt.data,
           (t.id << 16) + (CRC32(jt.k) % t.partitions) AS part
      FROM JSON_TABLE(p_events_json, '$[*]' COLUMNS(
               ord   FOR ORDINALITY,
               path  VARCHAR(4000) PATH '$.path',
               topic VARCHAR(500)  PATH '$.topic',
               k     VARCHAR(255)  PATH '$.key',
               data  LONGBLOB      PATH '$.data'
           )) AS jt
      JOIN namespaces n ON n.path_hash = UNHEX(MD5(jt.path))
      JOIN topics     t ON t.namespace_id = n.id AND t.name = jt.topic;

    -- -------------------------------------------------------------------------
    -- Step 2: Single-transaction bulk INSERT (InnoDB contiguous auto-increment)
    -- -------------------------------------------------------------------------
    START TRANSACTION;

        INSERT INTO events (data)
        SELECT data FROM _pub_batch ORDER BY ord;

        SET v_first_id = LAST_INSERT_ID();

        INSERT INTO unprocessed_events (id, partition_id)
        SELECT v_first_id + ROW_NUMBER() OVER (ORDER BY ord) - 1,
               part
          FROM _pub_batch
         ORDER BY ord;

    COMMIT;

    DROP TEMPORARY TABLE IF EXISTS _pub_batch;
END;
