
CREATE TABLE topics (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant      VARCHAR(255) NOT NULL,
    name        VARCHAR(255) NOT NULL,
    partitions  INT NOT NULL DEFAULT 16,
    INDEX idx_topics__name (name),
    CONSTRAINT u_topics__1 UNIQUE (tenant, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE partitions (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    topic_id            BIGINT NOT NULL,
    partition_number    INT NOT NULL,
    high_watermark      BIGINT DEFAULT 0 NOT NULL,
    FOREIGN KEY (topic_id) REFERENCES topics (id) ON DELETE CASCADE,
    CONSTRAINT u_partitions__1 UNIQUE (topic_id, partition_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE consumer_groups (
    id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant  VARCHAR(255) NOT NULL,
    name    VARCHAR(255) NOT NULL,
    CONSTRAINT u_consumer_groups__1 UNIQUE (tenant, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE subscriptions (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    consumer_group_id   BIGINT NOT NULL,
    topic_id            BIGINT NOT NULL,
    FOREIGN KEY (consumer_group_id) REFERENCES consumer_groups (id) ON DELETE CASCADE,
    FOREIGN KEY (topic_id) REFERENCES topics (id) ON DELETE CASCADE,
    CONSTRAINT u_subscriptions__1 UNIQUE (consumer_group_id, topic_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE subscription_offsets (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    subscription_id    BIGINT NOT NULL,
    partition_id        BIGINT NOT NULL,
    committed_offset    BIGINT NOT NULL DEFAULT 0,
    FOREIGN KEY (subscription_id)   REFERENCES subscriptions (id) ON DELETE CASCADE,
    FOREIGN KEY (partition_id)      REFERENCES partitions (id) ON DELETE CASCADE,
    CONSTRAINT u_consumer_groups__1 UNIQUE (subscription_id, partition_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE leases (
    subscription_offset_id  BIGINT PRIMARY KEY,
    owner                   VARCHAR(255) NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 1,
    acquired_at             DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at              DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    expires_at              DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_leases__expires_at (expires_at),
    INDEX idx_leases__owner (owner),
    FOREIGN KEY (subscription_offset_id) REFERENCES subscription_offsets(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;


CREATE TABLE events (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    ts              DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) NOT NULL,
    partition_id    BIGINT NOT NULL,
    data            JSON    NOT NULL,
    INDEX idx_events__partition (partition_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin
  ROW_FORMAT = DYNAMIC;

-- NOTE: For updating the high watermark, it was determined that
-- a trigger based approach offered the highest performance
-- when testing locally. It was compared against writing multiple rows from
-- the application, and using stored procedures.
-- A re-evaluation must be performed when running in a production-grade environment
CREATE TRIGGER tr_events__after_insert AFTER INSERT ON events FOR EACH ROW
BEGIN
    UPDATE partitions SET high_watermark = NEW.id WHERE id = NEW.partition_id;
END;


CREATE OR REPLACE ALGORITHM = MERGE VIEW subscription_offsets_view AS
SELECT
    so.id,
    so.subscription_id,
    so.partition_id,
    so.committed_offset,
    p.high_watermark
FROM subscription_offsets AS so
    INNER JOIN partitions AS p ON p.id = so.partition_id;


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
