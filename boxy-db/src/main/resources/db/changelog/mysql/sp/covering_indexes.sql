-- Covering indexes for poll query (item #57)
--
-- The poll query joins cursors → partitions → sequences → consumer_leases → events.
-- Without covering indexes, each row in the JOIN requires a random I/O back to the
-- clustered index.  These additions allow the optimizer to satisfy the poll query
-- from the index pages alone.
--
-- cursors: covering index for the candidate-selection step
--   SELECT c.id, c.partition_id, c.position, c.random_key
--     FROM cursors c
--    WHERE c.subscription_id = ?
--      AND JSON_CONTAINS(v_topic_ids, CAST(c.topic_id AS JSON), '$')
--      AND p.high_watermark > c.position
--
--   (subscription_id, topic_id, partition_id, random_key, position) covers:
--   - subscription_id: leading equality filter — the most selective condition
--   - topic_id:        second filter (JSON_CONTAINS or future direct JOIN)
--   - partition_id:    returned for the partitions JOIN; avoids row lookup
--   - random_key:      returned for probabilistic skip; avoids row lookup
--   - position:        returned for high_watermark > c.position comparison
--
-- consumer_leases: covering index for the commit procedure's lease release (item #53)
--   and for the poll procedure's lease lock check:
--   UPDATE consumer_leases SET locked_until = NULL
--    WHERE consumer_id = ? AND cursor_id IN (...)
--
--   (cursor_id, consumer_id, locked_until) covers:
--   - cursor_id:    leading lookup; cursor_id is already UNIQUE but without the
--                   extra columns the update still requires a table row fetch
--   - consumer_id:  second filter in commit; avoids table row lookup for validation
--   - locked_until: read in poll for the (cl.locked_until IS NULL OR cl.locked_until <= v_now)
--                   check; avoids table row lookup for this predicate

ALTER TABLE cursors
    ADD INDEX idx_cursors__poll_cover (subscription_id, topic_id, partition_id, random_key, position);

ALTER TABLE consumer_leases
    ADD INDEX idx_consumer_leases__commit_cover (cursor_id, consumer_id, locked_until);
