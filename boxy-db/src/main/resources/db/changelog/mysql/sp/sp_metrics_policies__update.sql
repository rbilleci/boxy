CREATE PROCEDURE sp_metrics_policies__update(
    IN p_metrics_refresh_interval INT)
BEGIN
    UPDATE metrics_policies
       SET metrics_refresh_interval = p_metrics_refresh_interval
     WHERE id = 1;
END;
