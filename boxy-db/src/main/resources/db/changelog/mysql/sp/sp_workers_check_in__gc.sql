CREATE PROCEDURE sp_workers_check_in__gc(IN p_node_id VARCHAR(255))
BEGIN
    -- GC: Delete leases that are stuck in a 'releasing' state.
    DELETE FROM leases
          WHERE state = 'RELEASING' AND released_at < (CURRENT_TIMESTAMP(3) - INTERVAL 10 SECOND);

    -- GC: For workers past the deadline (except this one), remove the leases and workers
    DELETE FROM leases
          WHERE worker_id IN (
                SELECT id FROM workers WHERE CURRENT_TIMESTAMP(3) >= heartbeat_deadline AND node_id <> p_node_id);
    DELETE FROM workers
          WHERE CURRENT_TIMESTAMP(3) >= heartbeat_deadline
            AND node_id <> p_node_id;
END;