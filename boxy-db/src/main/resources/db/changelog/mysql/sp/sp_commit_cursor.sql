CREATE PROCEDURE sp_commit_cursor(
    IN p_id BIGINT,
    IN p_offset BIGINT)
BEGIN
    UPDATE cursors SET committed_offset = p_offset WHERE id = p_id AND committed_offset < p_offset;
END;

