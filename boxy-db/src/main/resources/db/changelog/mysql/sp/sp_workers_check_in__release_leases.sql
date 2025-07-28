CREATE PROCEDURE sp_workers_check_in__release_leases(
    IN p_worker_id BIGINT,
    IN p_consumer_group_id BIGINT,
    IN p_max_leases INT,
    IN p_current_leases INT,
    OUT p_leases_released INT
)
BEGIN
    DECLARE p_limit INT;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN RESIGNAL; END;

    SET p_leases_released = 0;
    SET p_limit = p_current_leases - p_max_leases;
    
    -- If we have too many leases, release some
    IF p_current_leases > p_max_leases THEN
        -- Mark leases as RELEASING (smallest backlog first)
        -- Also set the released_at timestamp to track when the release started
        UPDATE leases l
        INNER JOIN subscription_offsets_view so ON so.id = l.subscription_offset_id
        INNER JOIN subscriptions s ON s.id = so.subscription_id
        SET l.state = 'RELEASING',
            l.released_at = CURRENT_TIMESTAMP(3)
        WHERE l.worker_id = p_worker_id
          AND s.consumer_group_id = p_consumer_group_id
          AND l.state = 'ACTIVE'
        ORDER BY (so.high_watermark - so.committed_offset) ASC
        LIMIT p_limit;
        
        -- Count released leases
        SET p_leases_released = ROW_COUNT();
    END IF;
END;