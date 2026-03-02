-- Add an index on unprocessed_events.partition_id (item #42).
--
-- The sequencer reads unprocessed_events ORDER BY id LIMIT batch_size, which
-- uses the primary key (id) scan and does NOT need partition_id.  However,
-- the high_watermark UPDATE in sp_sequence v1 joined unprocessed_events by
-- event_id (no index), and a partitioned sequencer (item #39) would filter by
-- partition_id range.
--
-- This index serves two purposes:
--   (a) Makes future partition-range scans efficient if partitioned sequencing
--       is implemented (item #39).
--   (b) Provides a covering index for any diagnostic queries on unprocessed_events
--       filtered by partition (e.g. "show me the backlog for partition P").
--
-- Impact on current workload: marginal.  INSERT into unprocessed_events gets a
-- second index write (primary key + this index).  The table is typically small
-- (events drain within milliseconds), so the overhead is negligible.
ALTER TABLE unprocessed_events
    ADD INDEX idx_ue__partition (partition_id);
