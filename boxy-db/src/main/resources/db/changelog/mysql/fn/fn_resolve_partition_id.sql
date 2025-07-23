-- FUNCTION TO DERIVE A PARTITION ID FROM A TOPIC ID AND P NUMBER
CREATE FUNCTION fn_resolve_partition_id(topic_id BIGINT, partition_number INT)
RETURNS BIGINT
DETERMINISTIC
BEGIN
    IF partition_number < 0 OR partition_number >= 65536 THEN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'The partition number must be from [0, 65536)';
    END IF;
    -- MULTIPLY BY 65536, then add the partition number
    RETURN (topic_id << 16) + partition_number;
END;