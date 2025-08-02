CREATE PROCEDURE sp_workers__gc(IN p_subscription_id BIGINT)
BEGIN
    DECLARE v_timestamp TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3);

    -- DELETE EXPIRED LEASES
    DELETE l
      FROM leases l
 LEFT JOIN cursors c ON c.id = l.cursor_id
 LEFT JOIN workers w ON w.id = l.worker_id
     WHERE
        -- CURSOR IS DELETED OR ITS FOR THIS SUBSCRIPTION
           (c.id IS NULL OR c.subscription_id = p_subscription_id)
       AND (
            -- WORKER DELETED OR EXPIRED
            w.id IS NULL
            -- WORKER EXPIRED
            OR v_timestamp >= w.heartbeat_deadline
            -- LEASE RELEASE PERIOD EXHAUSTED
            OR ((l.state = 'RELEASING') AND (v_timestamp >= l.release_deadline))
            );

    -- DELETE EXPIRED WORKERS
    DELETE w
      FROM workers w
 LEFT JOIN subscriptions s ON s.id = w.subscription_id
     WHERE
        -- RESTRICT TO THIS SUBSCRIPTION
           w.subscription_id = p_subscription_id
        -- WORKER EXPIRED OR SUBSCRIPTION DELETED
       AND ((v_timestamp >= w.heartbeat_deadline) OR (s.id IS NULL));

END;