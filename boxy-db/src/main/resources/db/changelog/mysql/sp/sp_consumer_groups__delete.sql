CREATE PROCEDURE sp_consumer_groups__delete(IN p_name VARCHAR(500))
BEGIN
    DELETE FROM consumer_groups WHERE name = p_name;
END;

