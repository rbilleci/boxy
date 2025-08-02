CREATE PROCEDURE sp_cursors__commit(
    IN p_id BIGINT,
    IN p_position BIGINT)
BEGIN
    UPDATE cursors SET position = p_position WHERE id = p_id AND position < p_position;
END;

