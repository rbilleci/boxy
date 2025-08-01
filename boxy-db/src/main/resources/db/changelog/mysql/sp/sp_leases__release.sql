CREATE PROCEDURE sp_leases__release(
    IN p_subscription_offset_id BIGINT,
    IN p_worker_id VARCHAR(255))
BEGIN
    DECLARE v_release_period INT;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN RESIGNAL; END;

    -- GET THE RELEASE PERIOD
    SELECT subscriptions.lease_release_period
      INTO v_release_period
      FROM workers
      JOIN subscriptions ON subscriptions.id = workers.subscription_id
     WHERE workers.id = p_worker_id;

    -- RELEASE THE LEASE
    UPDATE leases 
       SET state = 'RELEASING',
           released_at = CURRENT_TIMESTAMP(3),
           release_deadline = CURRENT_TIMESTAMP(3) + INTERVAL v_release_period SECOND
     WHERE subscription_offset_id = p_subscription_offset_id
       AND worker_id = p_worker_id;
END;
