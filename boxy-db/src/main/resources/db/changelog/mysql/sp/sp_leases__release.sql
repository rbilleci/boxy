CREATE PROCEDURE sp_leases__release(
    IN p_cursor_id BIGINT,
    IN p_worker_id VARCHAR(255))
BEGIN
    DECLARE v_timestamp TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3);
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
           released_at = v_timestamp,
           release_deadline = v_timestamp + INTERVAL v_release_period SECOND
     WHERE cursor_id = p_cursor_id
       AND worker_id = p_worker_id;
END;
