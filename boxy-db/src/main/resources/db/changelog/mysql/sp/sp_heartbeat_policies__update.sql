CREATE PROCEDURE sp_heartbeat_policies__update(
    IN p_heartbeat_deadline_multiplier DOUBLE,
    IN p_heartbeat_interval_baseline DOUBLE,
    IN p_heartbeat_interval_limit DOUBLE,
    IN p_heartbeat_target_qps DOUBLE)
BEGIN
    UPDATE heartbeat_policies
       SET heartbeat_deadline_multiplier = p_heartbeat_deadline_multiplier,
           heartbeat_interval_baseline = p_heartbeat_interval_baseline,
           heartbeat_interval_limit = p_heartbeat_interval_limit,
           heartbeat_target_qps = p_heartbeat_target_qps
     WHERE id = 1;
END;
