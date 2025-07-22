
CREATE TABLE topics (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant      VARCHAR(255) NOT NULL,
    name        VARCHAR(255) NOT NULL,
    partitions  INT NOT NULL DEFAULT 16,
    INDEX idx_topics__name (name),
    CONSTRAINT u_topics__1 UNIQUE (tenant, name)
) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_bin
    COMMENT='Stores logical topics (namespaces) per tenant, each with a configurable number of partitions';


CREATE TABLE partitions (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    topic_id            BIGINT NOT NULL,
    partition_number    INT NOT NULL,
    high_watermark      BIGINT DEFAULT 0 NOT NULL,
    FOREIGN KEY (topic_id) REFERENCES topics (id) ON DELETE CASCADE,
    CONSTRAINT u_partitions__1 UNIQUE (topic_id, partition_number)
) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_bin
    COMMENT='Tracks individual partitions for each topic, including the current high-watermark offset';


CREATE TABLE consumer_groups (
    id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant  VARCHAR(255) NOT NULL,
    name    VARCHAR(255) NOT NULL,
    CONSTRAINT u_consumer_groups__1 UNIQUE (tenant, name)
) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_bin
    COMMENT='Defines consumer groups per tenant, which will track offsets independently';


CREATE TABLE subscriptions (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    consumer_group_id   BIGINT NOT NULL,
    topic_id            BIGINT NOT NULL,
    FOREIGN KEY (consumer_group_id) REFERENCES consumer_groups (id) ON DELETE CASCADE,
    FOREIGN KEY (topic_id) REFERENCES topics (id) ON DELETE CASCADE,
    CONSTRAINT u_subscriptions__1 UNIQUE (consumer_group_id, topic_id)
) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_bin
    COMMENT='Joins consumer groups to the topics they subscribe to';


CREATE TABLE subscription_offsets (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    subscription_id    BIGINT NOT NULL,
    partition_id        BIGINT NOT NULL,
    committed_offset    BIGINT NOT NULL DEFAULT 0,
    FOREIGN KEY (subscription_id)   REFERENCES subscriptions (id) ON DELETE CASCADE,
    FOREIGN KEY (partition_id)      REFERENCES partitions (id) ON DELETE CASCADE,
    CONSTRAINT u_subscription_offsets__1 UNIQUE (subscription_id, partition_id)
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
    CONSTRAINT u_workers__1         UNIQUE (node_id, consumer_group_id),
    FOREIGN KEY (consumer_group_id) REFERENCES consumer_groups(id) ON DELETE CASCADE
) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_bin
    COMMENT='Registered worker nodes per consumer-group, with capacity weight and heartbeat timestamp';


CREATE TABLE leases (
    subscription_offset_id  BIGINT PRIMARY KEY,
    worker_id               BIGINT NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 1,
    acquired_at             DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at              DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    expires_at              DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_leases__expires_at (expires_at),
    INDEX idx_leases__worker (worker_id),
    FOREIGN KEY (subscription_offset_id) REFERENCES subscription_offsets(id) ON DELETE CASCADE,
    FOREIGN KEY (worker_id) REFERENCES workers(id) ON DELETE CASCADE
) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_bin
    COMMENT='Implements distributed locking for processing offsets—tracks worker, version, and expiration';


CREATE TABLE events (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    ts              DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) NOT NULL,
    partition_id    BIGINT NOT NULL,
    data            JSON    NOT NULL,
    INDEX idx_events__partition (partition_id, id)
) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_bin
    ROW_FORMAT = DYNAMIC
    COMMENT='Append-only event store per partition; JSON payloads in sequence order';


-- ========================================================
-- TRIGGER: tr_events__after_insert
--  After a new event row is appended:
--    • Advance the 'high_watermark' on the corresponding partition
--      to the new event’s auto-increment ID.
--  → Ensures partition.high_watermark is always up-to-date without
--    extra round-trips from the application.
-- ========================================================
CREATE TRIGGER tr_events__after_insert AFTER INSERT ON events FOR EACH ROW
BEGIN
    UPDATE partitions SET high_watermark = NEW.id WHERE id = NEW.partition_id;
END;


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
    LEFT JOIN leases as l ON l.subscription_offset_id = so.id AND l.expires_at >= CURRENT_TIMESTAMP(3)
WHERE l.subscription_offset_id IS NULL;
