CREATE PROCEDURE sp_consumers__gc(IN p_consumer_group_id BIGINT)
BEGIN
    DECLARE v_timestamp TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3);

    -- DELETE EXPIRED LEASES
    DELETE l
      FROM leases l
 LEFT JOIN cursors c ON c.id = l.cursor_id
 LEFT JOIN subscriptions st ON c.subscription_id = st.id
 LEFT JOIN consumers w ON w.id = l.consumer_id
     WHERE
        -- CURSOR IS DELETED OR IT'S FOR THIS CONSUMER GROUP
           (c.id IS NULL OR st.consumer_group_id = p_consumer_group_id)
       AND (
            -- CONSUMER DELETED OR EXPIRED
            w.id IS NULL
            -- CONSUMER EXPIRED
            OR v_timestamp >= w.heartbeat_deadline
            -- LEASE RELEASE PERIOD EXHAUSTED
            OR ((l.state = 'RELEASING') AND (v_timestamp >= l.release_deadline))
            );

    -- DELETE EXPIRED CONSUMERS
    DELETE w
      FROM consumers w
 LEFT JOIN consumer_groups s ON s.id = w.consumer_group_id
     WHERE
        -- RESTRICT TO THIS SUBSCRIPTION
           w.consumer_group_id = p_consumer_group_id
        -- CONSUMER EXPIRED OR CONSUMER GROUP DELETED
       AND ((v_timestamp >= w.heartbeat_deadline) OR (s.id IS NULL));

END;