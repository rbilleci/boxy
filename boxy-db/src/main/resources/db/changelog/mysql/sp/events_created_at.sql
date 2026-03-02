-- events_created_at: add created_at timestamp and retention index to events table.
--
-- Items #95-#96: Add created_at column and index for time-based retention queries.
--
-- Without created_at, the only way to identify old events is via sequence number,
-- which requires joining with the sequences table.  A direct timestamp column
-- enables efficient range scans for the cleanup procedure.
--
-- The DEFAULT CURRENT_TIMESTAMP(3) fills the column automatically on INSERT.
-- Existing rows will receive NULL (or the ALTER TABLE will set them to the epoch
-- if NOT NULL is requested without a default).  Here we use NOT NULL DEFAULT
-- CURRENT_TIMESTAMP(3) which sets existing rows to the current time during the ALTER.
-- This is acceptable for initial deployment; existing events are considered "current".
--
-- Item #96: idx_events__created_at enables efficient cleanup range scans:
--   DELETE FROM events WHERE created_at < ?  (with LIMIT for batch cleanup)
ALTER TABLE events
    ADD COLUMN created_at TIMESTAMP(3) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(3)
        COMMENT 'Wall-clock time when the event was published';

ALTER TABLE events
    ADD INDEX idx_events__created_at (created_at);
