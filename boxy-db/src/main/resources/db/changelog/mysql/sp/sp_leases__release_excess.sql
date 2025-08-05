CREATE PROCEDURE sp_leases__release_excess(
    IN p_consumer_id VARCHAR(36),
    IN p_subscription_id BIGINT,
    IN p_leases_to_release INT)
BEGIN
    DECLARE v_release_period INT;

    -- GET THE RELEASE PERIOD
    SELECT lease_release_period
      INTO v_release_period
      FROM lease_policies;

    -- Release leases when we have too many
    -- Among the leases held, identify:
    --  1) those with the least queued work (so transfer is quick and less disruptive).
    --  2) Among those, pick the one most recently acquired (minimizes disruption to established processing).
    UPDATE leases l
    JOIN (
      SELECT t.cursor_id
      FROM (
        SELECT
          l.cursor_id,
          l.acquired_at,
          (p.high_watermark - c.position) AS lag_metric,
          ROW_NUMBER() OVER (ORDER BY (p.high_watermark - c.position) ASC) AS rn,
          COUNT(1) OVER () AS total_leases
        FROM leases l
        JOIN cursors c ON l.cursor_id = c.id
        JOIN partitions p ON l.partition_id = p.id
        WHERE l.consumer_id = p_consumer_id
          AND l.subscription_id = p_subscription_id
          AND l.state = 'ACTIVE'
      ) t
      WHERE t.rn <= CEIL(t.total_leases * 0.5)
      ORDER BY t.acquired_at DESC
      LIMIT p_leases_to_release
    ) th ON l.cursor_id = th.cursor_id
    SET l.state = 'RELEASING',
        l.released_at = CURRENT_TIMESTAMP(3),
        l.release_deadline = CURRENT_TIMESTAMP(3) + INTERVAL v_release_period SECOND;
END;
