CREATE PROCEDURE sp_topics__delete(
    IN p_namespace_id BIGINT,
    IN p_name VARCHAR(255))
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN RESIGNAL; END;
    DELETE FROM topics WHERE namespace_id = p_namespace_id AND name = p_name;
END;

