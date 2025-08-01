CREATE TABLE namespaces (
    id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant  VARCHAR(255) NOT NULL,
    name    VARCHAR(255) NOT NULL,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    last_modified_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT u_namespaces UNIQUE (tenant, name)
) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_bin
    COMMENT='Stores namespaces per tenant';

CREATE TABLE topics (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    namespace_id        BIGINT NOT NULL,
    name                VARCHAR(255) NOT NULL,
    partitions          INT NOT NULL DEFAULT 16,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    last_modified_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_topics__cover (namespace_id, name, id, partitions),
    CONSTRAINT u_topics UNIQUE (namespace_id, name),
    FOREIGN KEY (namespace_id) REFERENCES namespaces (id)
) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_bin
    COMMENT='Stores logical topics per namespace';

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
    COMMENT='Tracks partitions for each topic';

CREATE TABLE subscriptions (
    id                              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant                          VARCHAR(255) NOT NULL,
    name                            VARCHAR(255) NOT NULL,
    heartbeat_deadline_multiplier   DOUBLE NOT NULL DEFAULT 5.0,
    heartbeat_interval_baseline     DOUBLE NOT NULL DEFAULT 3.0,
    heartbeat_interval              DOUBLE NOT NULL DEFAULT 15.0,
    heartbeat_interval_limit        DOUBLE NOT NULL DEFAULT 60.00,
    heartbeat_target_qps            DOUBLE NOT NULL DEFAULT 10.0,
    metrics_refresh_interval        INT NOT NULL DEFAULT 3,
    lease_release_period            INT NOT NULL DEFAULT 10,
    active_partitions               INT NOT NULL DEFAULT 0,
    active_workers                  INT NOT NULL DEFAULT 0,
    active_workers_limit            INT NOT NULL DEFAULT 16,
    active_workers_weight           DOUBLE NOT NULL DEFAULT 0,
    last_modified_at                DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT u_subscriptions UNIQUE (tenant, name)
) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_bin
    COMMENT='Defines subscriptions per tenant which store consumption state';

CREATE TABLE subscription_topics (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    subscription_id  BIGINT NOT NULL,
    topic_id         BIGINT NOT NULL,
    created_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    FOREIGN KEY (subscription_id) REFERENCES subscriptions (id) ON DELETE CASCADE,
    FOREIGN KEY (topic_id) REFERENCES topics (id) ON DELETE CASCADE,
    CONSTRAINT u_subscription_topics UNIQUE (subscription_id, topic_id),
    INDEX idx_subscription_topics__subscription (subscription_id, id)
) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_bin
    COMMENT='Links subscriptions to the topics they consume';

CREATE TABLE subscription_offsets (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    subscription_id  BIGINT NOT NULL,
    partition_id     BIGINT NOT NULL,
    random_key       INT    NOT NULL,
    committed_offset BIGINT NOT NULL DEFAULT 0,
    FOREIGN KEY (subscription_id)   REFERENCES subscription_topics (id) ON DELETE CASCADE,
    FOREIGN KEY (partition_id)      REFERENCES partitions (id) ON DELETE CASCADE,
    CONSTRAINT u_subscription_offsets UNIQUE (subscription_id, partition_id),
    INDEX idx_subscription_offsets__random_1 (random_key, id),
    INDEX idx_subscription_offsets__random_2 (subscription_id, random_key, id)
) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_bin
    COMMENT='Maintains the last committed offset per partition for each subscription/topic pair';

CREATE INDEX idx_subscription_offsets__random_key ON subscription_offsets(random_key);

CREATE TABLE workers (
    id                   VARCHAR(255) PRIMARY KEY,
    subscription_id      BIGINT       NOT NULL,
    weight               DOUBLE       NOT NULL DEFAULT 1,
    heartbeat_detected_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    heartbeat_interval   DOUBLE       NOT NULL,
    heartbeat_deadline   DATETIME(3)  NOT NULL,
    INDEX idx_workers__subscription (subscription_id),
    INDEX idx_workers__heartbeat_detected_at (heartbeat_detected_at),
    FOREIGN KEY (subscription_id) REFERENCES subscriptions(id)
) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_bin
    COMMENT='Registered workers per subscription, with capacity weight and heartbeat timestamp';

CREATE TABLE leases (
    subscription_offset_id  BIGINT PRIMARY KEY,
    worker_id               VARCHAR(255) NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 1,
    acquired_at             DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    released_at             DATETIME(3) NULL,
    release_deadline        DATETIME(3) NULL,
    state                   ENUM('ACTIVE', 'RELEASING') NOT NULL DEFAULT 'ACTIVE',
    INDEX idx_leases__state (subscription_offset_id, state),
    INDEX idx_leases__worker (worker_id),
    FOREIGN KEY (subscription_offset_id) REFERENCES subscription_offsets(id),
    FOREIGN KEY (worker_id) REFERENCES workers(id)
) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_bin
    COMMENT='Implements distributed locking for processing offsets—tracks worker, version, and state';

CREATE TABLE events (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    published_at    DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) NOT NULL,
    partition_id    BIGINT NOT NULL,
    data            JSON   NOT NULL,
    INDEX idx_events__cover(partition_id, id)
) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_bin
    ROW_FORMAT = DYNAMIC
    COMMENT='Append-only event store per partition; JSON payloads in sequence order';

CREATE TABLE topics_cache (
  tenant       VARCHAR(255)  NOT NULL,
  namespace    VARCHAR(255)  NOT NULL,
  topic        VARCHAR(255)  NOT NULL,
  topic_id     BIGINT        NOT NULL,
  partitions   INT NOT NULL,
  PRIMARY KEY (tenant, namespace, topic)
) ENGINE=MEMORY;

-- ========================================================
-- VIEW: unleased_subscription_offsets_view
-- List subscription offsets leases that are not leased and have active work to perform
CREATE OR REPLACE ALGORITHM = MERGE VIEW unleased_subscription_offsets_view AS
    SELECT so.*
      FROM subscription_offsets so
      JOIN partitions p     ON so.partition_id = p.id
 LEFT JOIN leases l         ON so.id = l.subscription_offset_id
 LEFT JOIN workers w        ON l.worker_id = w.id
    -- A lease is available if:
    -- 1. No lease exists for this subscription offset (l.subscription_offset_id IS NULL)
    -- 2. Or a lease exists but the worker has expired
     WHERE so.committed_offset < p.high_watermark
       AND (l.subscription_offset_id IS NULL OR w.heartbeat_detected_at < w.heartbeat_deadline);

-- ========================================================
-- VIEW: leased_subscription_offsets_view
-- List active leases. Normal use will filter by worker_id
CREATE OR REPLACE ALGORITHM = MERGE VIEW leased_subscription_offsets_view AS
    SELECT so.*,
           w.id AS worker_id,
           l.subscription_offset_id AS lease_id,
           l.acquired_at
      FROM workers w
      JOIN leases l ON w.id = l.worker_id
      JOIN subscription_offsets so ON l.subscription_offset_id = so.id
     WHERE w.heartbeat_detected_at < w.heartbeat_deadline
       AND l.state = 'ACTIVE'
  ORDER BY worker_id, so.id;
