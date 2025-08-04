CREATE PROCEDURE sp_leases__release(
    IN p_cursor_id BIGINT,
    IN p_consumer_id VARCHAR(36))
BEGIN
    DECLARE v_timestamp TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3);
    DECLARE v_release_period INT;

    -- GET THE RELEASE PERIOD
    SELECT consumer_groups.lease_release_period
      INTO v_release_period
      FROM consumers
      JOIN consumer_groups ON consumer_groups.id = consumers.consumer_group_id
     WHERE consumers.id = p_consumer_id;

    -- RELEASE THE LEASE
    UPDATE leases 
       SET state = 'RELEASING',
           released_at = v_timestamp,
           release_deadline = v_timestamp + INTERVAL v_release_period SECOND
     WHERE cursor_id = p_cursor_id
       AND consumer_id = p_consumer_id;
END;
