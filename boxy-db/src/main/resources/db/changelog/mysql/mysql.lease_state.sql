-- Add state column to leases table
ALTER TABLE leases
    ADD COLUMN state ENUM('ACTIVE', 'RELEASING') NOT NULL DEFAULT 'ACTIVE' AFTER expires_at,
    ADD INDEX idx_leases__state (state);

-- Update leases_available_view to exclude leases in RELEASING state
CREATE OR REPLACE ALGORITHM = MERGE VIEW leases_available_view AS
SELECT
    so.id,
    so.subscription_id,
    so.partition_id,
    so.committed_offset,
    p.high_watermark
FROM subscription_offsets AS so
    INNER JOIN partitions AS p ON p.id = so.partition_id AND p.high_watermark > so.committed_offset
    LEFT JOIN leases as l ON l.subscription_offset_id = so.id 
        AND (l.expires_at >= CURRENT_TIMESTAMP(3) OR l.state = 'RELEASING')
WHERE l.subscription_offset_id IS NULL;