CREATE PROCEDURE sp_workers_check_in__release_leases(
    IN p_worker_id VARCHAR(255),
    IN p_max_leases INT,
    IN p_current_leases INT,
    OUT p_leases_released INT
)
BEGIN
    DECLARE p_limit INT;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN RESIGNAL; END;

    SET p_leases_released = 0;
    SET p_limit = p_current_leases - p_max_leases;
    
    -- Release leases when we have too many
    -- Among the leases held, identify:
    --  1) those with the least queued work (so transfer is quick and less disruptive).
    --  2) Among those, pick the one most recently acquired (minimizes disruption to established processing).
    IF p_current_leases > p_max_leases THEN

        UPDATE leases l
        JOIN (
          SELECT t.subscription_offset_id
          FROM (
            SELECT
              lsov.subscription_offset_id,
              lsov.acquired_at,
              (p.high_watermark - lsov.committed_offset) AS lag_metric,
              ROW_NUMBER() OVER (ORDER BY (p.high_watermark - lsov.committed_offset) ASC) AS rn,
              COUNT(1) OVER () AS total_leases
            FROM leased_subscription_offsets_view lsov
            JOIN partitions p ON lsov.partition_id = p.id
            WHERE lsov.worker_id = p_worker_id
          ) t
          WHERE t.rn <= CEIL(t.total_leases * 0.5)
          ORDER BY t.acquired_at DESC
          LIMIT p_limit
        ) th ON l.subscription_offset_id = th.subscription_offset_id
        SET l.state = 'RELEASING',
            l.released_at = CURRENT_TIMESTAMP(3);

        -- Finally return the count of released leases
        SET p_leases_released = ROW_COUNT();
    END IF;
END;