DROP PROCEDURE IF EXISTS sp_delete_consumer_group;
CREATE PROCEDURE sp_delete_consumer_group(IN p_id BIGINT)
BEGIN
    DELETE FROM consumer_groups WHERE id = p_id;
END;

