-- Create a new table to store precomputed values for consumer groups
CREATE TABLE consumer_group_stats (
    consumer_group_id    BIGINT PRIMARY KEY,
    active_workers_count INT NOT NULL DEFAULT 0,
    total_weight         INT NOT NULL DEFAULT 0,
    active_partitions_count INT NOT NULL DEFAULT 0,
    heartbeat_interval   INT NOT NULL DEFAULT 3,
    lease_ttl_base       INT NOT NULL DEFAULT 15,
    last_updated         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    FOREIGN KEY (consumer_group_id) REFERENCES consumer_groups (id) ON DELETE CASCADE
) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_bin
    COMMENT='Stores precomputed statistics for consumer groups to optimize worker check-in';