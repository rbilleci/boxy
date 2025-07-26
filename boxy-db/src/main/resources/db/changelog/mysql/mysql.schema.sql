
CREATE TABLE topics (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant      VARCHAR(255) NOT NULL,
    name        VARCHAR(255) NOT NULL,
    partitions  INT NOT NULL DEFAULT 16,
    INDEX idx_topics__cover (tenant, name, id, partitions),
    CONSTRAINT u_topics UNIQUE (tenant, name)
) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_bin
    COMMENT='Stores logical topics (namespaces) per tenant, each with a configurable number of partitions';

CREATE TABLE partitions (
    id                  BIGINT AS ((topic_id << 16) + partition_number) STORED PRIMARY KEY,
    topic_id            BIGINT NOT NULL,
    partition_number    INT NOT NULL,
    high_watermark      BIGINT DEFAULT 0 NOT NULL,
    INDEX idx_partitions__cover (topic_id, partition_number, id),
    FOREIGN KEY (topic_id) REFERENCES topics (id),
    CONSTRAINT u_partitions UNIQUE (topic_id, partition_number)
) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_bin
    COMMENT='Tracks individual partitions for each topic, including the current high-watermark offset';


CREATE TABLE consumer_groups (
    id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant  VARCHAR(255) NOT NULL,
    name    VARCHAR(255) NOT NULL,
    CONSTRAINT u_consumer_groups UNIQUE (tenant, name)
) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_bin
    COMMENT='Defines consumer groups per tenant, which will track offsets independently';

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


CREATE TABLE subscriptions (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    consumer_group_id   BIGINT NOT NULL,
    topic_id            BIGINT NOT NULL,
    FOREIGN KEY (consumer_group_id) REFERENCES consumer_groups (id) ON DELETE CASCADE,
    FOREIGN KEY (topic_id) REFERENCES topics (id) ON DELETE CASCADE,
    CONSTRAINT u_subscriptions UNIQUE (consumer_group_id, topic_id)
) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_bin
    COMMENT='Joins consumer groups to the topics they subscribe to';


CREATE TABLE subscription_offsets (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    subscription_id     BIGINT NOT NULL,
    partition_id        BIGINT NOT NULL,
    committed_offset    BIGINT NOT NULL DEFAULT 0,
    FOREIGN KEY (subscription_id)   REFERENCES subscriptions (id) ON DELETE CASCADE,
    FOREIGN KEY (partition_id)      REFERENCES partitions (id) ON DELETE CASCADE,
    CONSTRAINT u_subscription_offsets UNIQUE (subscription_id, partition_id)
) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_bin
    COMMENT='Maintains the last committed offset per partition for each subscription';


CREATE TABLE workers (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    node_id              VARCHAR(255) NOT NULL,
    consumer_group_id    BIGINT       NOT NULL,
    weight               INT          NOT NULL DEFAULT 1,
    last_heartbeat       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_workers__consumer_group (consumer_group_id),
    INDEX idx_workers___last_heartbeat (last_heartbeat),
    CONSTRAINT u_workers UNIQUE (node_id, consumer_group_id)
) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_bin
    COMMENT='Registered worker nodes per consumer-group, with capacity weight and heartbeat timestamp';


CREATE TABLE leases (
    subscription_offset_id  BIGINT PRIMARY KEY,
    worker_id               BIGINT NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 1,
    acquired_at             DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    released_at             DATETIME(3) NULL,
    state                   ENUM('ACTIVE', 'RELEASING') NOT NULL DEFAULT 'ACTIVE',
    INDEX idx_leases__state (state),
    INDEX idx_leases__worker (worker_id),
    FOREIGN KEY (subscription_offset_id) REFERENCES subscription_offsets(id),
    FOREIGN KEY (worker_id) REFERENCES workers(id)
) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_bin
    COMMENT='Implements distributed locking for processing offsets—tracks worker, version, and state';


CREATE TABLE events (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    ts              DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) NOT NULL,
    partition_id    BIGINT NOT NULL,
    data            JSON   NOT NULL,
    INDEX idx_events__cover(partition_id, id)
) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_bin
    ROW_FORMAT = DYNAMIC
    COMMENT='Append-only event store per partition; JSON payloads in sequence order';


-- ========================================================
-- VIEW: subscription_offsets_view
--  Provides for each subscription-partition pair:
--    • the last committed offset
--    • the current high-watermark of that partition
--  → Useful for monitoring consumer lag and for tooling that needs
--    both committed and available offsets in one place.
-- ========================================================
CREATE OR REPLACE ALGORITHM = MERGE VIEW subscription_offsets_view AS
SELECT
    so.id,
    so.subscription_id,
    so.partition_id,
    so.committed_offset,
    p.high_watermark
FROM subscription_offsets AS so
    INNER JOIN partitions AS p ON p.id = so.partition_id;


CREATE TABLE topics_cache (
  tenant      VARCHAR(255)  NOT NULL,
  topic       VARCHAR(255)  NOT NULL,
  topic_id    BIGINT        NOT NULL,
  partitions  INT NOT NULL,
  PRIMARY KEY (tenant, topic)
) ENGINE=MEMORY;

-- ========================================================
-- VIEW: leases_available_view
--  Shows only those subscription-partition pairs which:
--    • have new events available (high_watermark > committed_offset)
--    • are not currently leased (no unexpired lease row exists)
--  → Designed for workers to pick up “workable” partitions
--    without racing other consumers.
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
