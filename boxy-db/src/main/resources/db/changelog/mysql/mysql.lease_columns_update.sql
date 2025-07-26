-- Remove expires_at and updated_at columns, add released_at column
ALTER TABLE leases
    DROP COLUMN expires_at,
    DROP COLUMN updated_at,
    ADD COLUMN released_at DATETIME(3) NULL AFTER acquired_at,
    DROP INDEX idx_leases__expires_at;

-- Update leases_available_view to use worker's last_heartbeat instead of lease's expires_at
CREATE OR REPLACE ALGORITHM = MERGE VIEW leases_available_view AS
SELECT
    so.id,
    so.subscription_id,
    so.partition_id,
    so.committed_offset,
    p.high_watermark
FROM subscription_offsets AS so
    INNER JOIN partitions AS p ON p.id = so.partition_id AND p.high_watermark > so.committed_offset
    LEFT JOIN leases AS l ON l.subscription_offset_id = so.id
    LEFT JOIN workers AS w ON w.id = l.worker_id
    -- A lease is available if:
    -- 1. No lease exists for this subscription offset (l.subscription_offset_id IS NULL)
    -- 2. The lease exists but the worker has expired (ACTIVE state + worker heartbeat is old)
    -- Note: Leases in RELEASING state are not available
    WHERE l.subscription_offset_id IS NULL OR 
          (l.state = 'ACTIVE' AND w.last_heartbeat < CURRENT_TIMESTAMP(3) - INTERVAL 10 SECOND);