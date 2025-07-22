CREATE PROCEDURE sp_commit_offset(IN p_id BIGINT, IN p_offset BIGINT)
BEGIN
    UPDATE subscription_offsets
    SET committed_offset = p_offset
    WHERE id = p_id AND committed_offset < p_offset;
END;

