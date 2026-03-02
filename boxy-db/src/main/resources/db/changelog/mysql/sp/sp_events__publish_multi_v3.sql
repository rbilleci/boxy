-- sp_events__publish_multi (v3 — EXIT HANDLER with ROLLBACK + temp table cleanup, Section 2 item 73)
-- v2: replaced N-loop with single-transaction bulk INSERT (changeset 2)
-- v3: adds EXIT HANDLER with ROLLBACK and temp table cleanup
DROP PROCEDURE IF EXISTS sp_events__publish_multi;
CREATE PROCEDURE sp_events__publish_multi(IN p_events_json JSON)
BEGIN
    DECLARE v_first_id BIGINT;

    -- EXIT HANDLER: on any SQL error, roll back the bulk INSERT transaction,
    -- drop the temp table (if it was created before the error), and re-signal.
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        DROP TEMPORARY TABLE IF EXISTS _pub_batch;
        RESIGNAL;
    END;

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
