CREATE PROCEDURE sp_lease_policies__update(
    IN p_active_consumers_limit INT,
    IN p_lease_release_period INT)
BEGIN
    UPDATE lease_policies
       SET active_consumers_limit = p_active_consumers_limit,
           lease_release_period = p_lease_release_period
     WHERE id = 1;
END;
