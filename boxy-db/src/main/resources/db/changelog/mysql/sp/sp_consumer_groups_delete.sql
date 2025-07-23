CREATE PROCEDURE sp_consumer_groups_delete(
    IN p_id BIGINT)
BEGIN
    DELETE FROM consumer_groups WHERE id = p_id;
END;

