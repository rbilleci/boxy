CREATE PROCEDURE sp_consumers__gc(IN p_subscription_id BIGINT)
BEGIN
    DECLARE v_timestamp TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3);

    -- DELETE EXPIRED LEASES
    DELETE l
      FROM leases l
      JOIN consumers w ON w.id = l.consumer_id
     WHERE l.subscription_id = p_subscription_id
       AND (
            v_timestamp >= w.heartbeat_deadline
            OR ((l.state = 'RELEASING') AND (v_timestamp >= l.release_deadline))
           );

    -- DELETE EXPIRED CONSUMERS
    DELETE w
      FROM consumers w
 LEFT JOIN subscriptions s ON s.id = w.subscription_id
     WHERE
        -- RESTRICT TO THIS SUBSCRIPTION
           w.subscription_id = p_subscription_id
        -- CONSUMER EXPIRED OR SUBSCRIPTION DELETED
       AND ((v_timestamp >= w.heartbeat_deadline) OR (s.id IS NULL));

END;