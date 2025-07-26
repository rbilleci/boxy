-- Migration script to initialize consumer_group_stats for existing consumer groups
INSERT INTO consumer_group_stats (
    consumer_group_id,
    active_workers_count,
    total_weight,
    active_partitions_count,
    heartbeat_interval,
    lease_ttl_base,
    last_updated
)
SELECT 
    cg.id,
    0,  -- active_workers_count (will be updated on next check-in)
    0,  -- total_weight (will be updated on next check-in)
    0,  -- active_partitions_count (will be updated on next check-in)
    3,  -- heartbeat_interval (default minimum)
    15, -- lease_ttl_base (default 5x heartbeat_interval)
    CURRENT_TIMESTAMP(3)
FROM consumer_groups cg
LEFT JOIN consumer_group_stats cgs ON cg.id = cgs.consumer_group_id
WHERE cgs.consumer_group_id IS NULL;