CREATE PROCEDURE sp_workers_check_in__release_leases(
    IN p_worker_id BIGINT,
    IN p_consumer_group_id BIGINT,
    IN p_max_leases INT,
    IN p_current_leases INT,
    OUT p_leases_released INT
)
BEGIN
    DECLARE p_limit INT;
    SET p_leases_released = 0;
    SET p_limit = p_current_leases - p_max_leases;
    
    -- If we have too many leases, release some
    IF p_current_leases > p_max_leases THEN
        -- Find leases to release (smallest backlog first)
        INSERT INTO temp_leases_removed
        SELECT l.subscription_offset_id
        FROM leases l
        INNER JOIN subscription_offsets_view so ON so.id = l.subscription_offset_id
        INNER JOIN subscriptions s ON s.id = so.subscription_id
        WHERE l.worker_id = p_worker_id
          AND s.consumer_group_id = p_consumer_group_id
        ORDER BY (so.high_watermark - so.committed_offset) ASC
        LIMIT p_limit;
        
        -- Mark the selected leases as RELEASING instead of deleting them
        -- Also set the released_at timestamp to track when the release started
        UPDATE leases
        SET state = 'RELEASING',
            released_at = CURRENT_TIMESTAMP(3)
        WHERE worker_id = p_worker_id
          AND subscription_offset_id IN (SELECT subscription_offset_id FROM temp_leases_removed);
        
        -- Count released leases
        SELECT COUNT(*) INTO p_leases_released FROM temp_leases_removed;
    END IF;
END;