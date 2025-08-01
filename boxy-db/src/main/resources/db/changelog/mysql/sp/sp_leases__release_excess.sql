CREATE PROCEDURE sp_leases__release_excess(
    IN p_worker_id VARCHAR(255),
    IN p_subscription_id BIGINT,
    IN p_leases_to_release INT
)
BEGIN
    DECLARE v_release_period INT;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN RESIGNAL; END;

    -- GET THE RELEASE PERIOD
    SELECT lease_release_period
      INTO v_release_period
      FROM subscriptions
     WHERE id = p_subscription_id;

    -- Release leases when we have too many
    -- Among the leases held, identify:
    --  1) those with the least queued work (so transfer is quick and less disruptive).
    --  2) Among those, pick the one most recently acquired (minimizes disruption to established processing).
    UPDATE leases l
    JOIN (
      SELECT t.id
      FROM (
        SELECT
          lsov.id,
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
      LIMIT p_leases_to_release
    ) th ON l.subscription_offset_id = th.id
    SET l.state = 'RELEASING',
        l.released_at = CURRENT_TIMESTAMP(3),
        l.release_deadline = CURRENT_TIMESTAMP(3) + INTERVAL v_release_period SECOND;
END;