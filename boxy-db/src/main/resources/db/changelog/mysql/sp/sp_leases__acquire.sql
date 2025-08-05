CREATE PROCEDURE sp_leases__acquire(
    IN p_consumer_id VARCHAR(36),
    IN p_subscription_id BIGINT,
    IN p_leases_to_acquire INT
)
BEGIN
    DECLARE v_random_key INT;
    DECLARE p_limit INT;
    DECLARE v_limit INT;
    SET v_limit = p_leases_to_acquire;

    -- PASS 1: RANDOM PIVOT
    SET v_random_key = fn_random_int();
    INSERT INTO leases (cursor_id, subscription_id, topic_id, partition_id, consumer_id, state)
         SELECT id, uc.subscription_id, uc.topic_id, uc.partition_id, p_consumer_id, 'ACTIVE'
            FROM unleased_cursors_view uc
          JOIN consumer_subscriptions cs ON uc.topic_id = cs.topic_id
          WHERE uc.subscription_id = p_subscription_id
            AND cs.consumer_id = p_consumer_id
            AND uc.random_key >= v_random_key -- Start from a random pivot
      ORDER BY uc.random_key, id
          LIMIT v_limit
             ON DUPLICATE KEY UPDATE
                consumer_id = VALUES(consumer_id),
                version = leases.version + 1,
                released_at = NULL,
                state = 'ACTIVE';
    SET v_limit = v_limit - ROW_COUNT();


    -- PASS 2: WRAP AROUND FROM THE START
    IF (v_limit > 0) THEN
        INSERT INTO leases (cursor_id, subscription_id, topic_id, partition_id, consumer_id, state)
             SELECT id, uc.subscription_id, uc.topic_id, uc.partition_id, p_consumer_id, 'ACTIVE'
                FROM unleased_cursors_view uc
              JOIN consumer_subscriptions cs ON uc.topic_id = cs.topic_id
              WHERE uc.subscription_id = p_subscription_id
                AND cs.consumer_id = p_consumer_id
                AND uc.random_key < v_random_key
          ORDER BY uc.random_key, id
              LIMIT v_limit
                 ON DUPLICATE KEY UPDATE
                    consumer_id = VALUES(consumer_id),
                    version = leases.version + 1,
                    released_at = NULL,
                    state = 'ACTIVE';
    END IF;
END;