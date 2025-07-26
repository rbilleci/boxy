-- Modify the leases table to remove expires_at and updated_at columns
-- and add status and release_started_at columns

-- First, drop the existing index on expires_at
ALTER TABLE leases DROP INDEX idx_leases__expires_at;

-- Add new columns
ALTER TABLE leases 
    ADD COLUMN status ENUM('ACTIVE', 'RELEASING') NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN release_started_at DATETIME(3) NULL;

-- Create index on status for efficient queries
ALTER TABLE leases ADD INDEX idx_leases__status (status);

-- Create index on release_started_at for efficient cleanup
ALTER TABLE leases ADD INDEX idx_leases__release_started_at (release_started_at);

-- Drop the expires_at and updated_at columns
ALTER TABLE leases DROP COLUMN expires_at;
ALTER TABLE leases DROP COLUMN updated_at;

-- Update the leases_available_view to use worker's last_heartbeat instead of lease's expires_at
CREATE OR REPLACE ALGORITHM = MERGE VIEW leases_available_view AS
SELECT
    so.id,
    so.subscription_id,
    so.partition_id,
    so.committed_offset,
    p.high_watermark
FROM subscription_offsets AS so
    INNER JOIN partitions AS p ON p.id = so.partition_id AND p.high_watermark > so.committed_offset
    LEFT JOIN leases as l ON l.subscription_offset_id = so.id AND l.status = 'ACTIVE'
WHERE l.subscription_offset_id IS NULL;