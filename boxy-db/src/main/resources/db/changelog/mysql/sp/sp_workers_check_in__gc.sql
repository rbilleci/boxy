CREATE PROCEDURE sp_workers_check_in__gc(IN p_node_id VARCHAR(255))
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN RESIGNAL; END;

    -- GC: Delete leases that are stuck in a 'releasing' state.
    -- Use the release_deadline from the consumer group instead of a hard-coded value
    DELETE l FROM leases l
    JOIN workers w ON l.worker_id = w.id
    JOIN consumer_groups cg ON w.consumer_group_id = cg.id
    WHERE l.state = 'RELEASING' 
      AND l.released_at < (CURRENT_TIMESTAMP(3) - INTERVAL cg.release_deadline SECOND);

    -- GC: For workers past the deadline (except this one), remove the leases and workers
    DELETE FROM leases
          WHERE worker_id IN (
                SELECT id FROM workers WHERE CURRENT_TIMESTAMP(3) >= heartbeat_deadline AND node_id <> p_node_id);
    DELETE FROM workers
          WHERE CURRENT_TIMESTAMP(3) >= heartbeat_deadline
            AND node_id <> p_node_id;
END;