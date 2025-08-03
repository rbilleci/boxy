CREATE TABLE namespaces (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    parent_id        BIGINT NULL,
    name             VARCHAR(500)    NOT NULL,
    created_at       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    last_modified_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT u_namespaces__parent UNIQUE (parent_id, name),
    FOREIGN KEY (parent_id) REFERENCES namespaces(id) ON DELETE CASCADE
) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_bin
    COMMENT='Stores namespaces hierarchically';

CREATE TABLE topics (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    namespace_id        BIGINT NOT NULL,
    name                VARCHAR(500) NOT NULL,
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
    name                            VARCHAR(500) NOT NULL,
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
    CONSTRAINT u_subscriptions UNIQUE (name)
) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_bin
    COMMENT='Defines subscriptions which store consumption state';

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

CREATE TABLE cursors (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    subscription_id  BIGINT NOT NULL,
    partition_id     BIGINT NOT NULL,
    random_key       INT    NOT NULL,
    position         BIGINT NOT NULL DEFAULT 0,
    FOREIGN KEY (subscription_id)   REFERENCES subscription_topics (id) ON DELETE CASCADE,
    FOREIGN KEY (partition_id)      REFERENCES partitions (id) ON DELETE CASCADE,
    CONSTRAINT u_cursors UNIQUE (subscription_id, partition_id),
    INDEX idx_cursors__random_1 (random_key, id),
    INDEX idx_cursors__random_2 (subscription_id, random_key, id)
) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_bin
    COMMENT='Maintains the last cursor position for each subscription/topic-partition';

CREATE INDEX idx_cursors__random_key ON cursors(random_key);

CREATE TABLE workers (
    id                   VARCHAR(36)  PRIMARY KEY,
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
    cursor_id           BIGINT PRIMARY KEY,
    worker_id           VARCHAR(36) NOT NULL,
    version             BIGINT NOT NULL DEFAULT 1,
    acquired_at         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    released_at         DATETIME(3) NULL,
    release_deadline    DATETIME(3) NULL,
    state               ENUM('ACTIVE', 'RELEASING') NOT NULL DEFAULT 'ACTIVE',
    INDEX idx_leases__state (cursor_id, state),
    INDEX idx_leases__worker (worker_id),
    FOREIGN KEY (cursor_id) REFERENCES cursors(id),
    FOREIGN KEY (worker_id) REFERENCES workers(id)
) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_bin
    COMMENT='Implements distributed locking for processing cursor positions—tracks worker, version, and state';

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
  path_hash    BINARY(16)    NOT NULL,
  path         VARCHAR(4000) NOT NULL,
  topic        VARCHAR(500)  NOT NULL,
  topic_id     BIGINT        NOT NULL,
  partitions   INT NOT NULL,
  PRIMARY KEY (path_hash, topic)
) ENGINE=MEMORY;

-- ========================================================
-- VIEW: unleased_cursors_view
-- List cursors leases that are not leased and have active work to perform
CREATE OR REPLACE ALGORITHM = MERGE VIEW unleased_cursors_view AS
    SELECT c.*
      FROM cursors c
      JOIN partitions p     ON c.partition_id = p.id
 LEFT JOIN leases l         ON c.id = l.cursor_id
 LEFT JOIN workers w        ON l.worker_id = w.id
    -- A lease is available if:
    -- 1. No lease exists for this cursor (l.cursor_id IS NULL)
    -- 2. Or a lease exists but the worker has expired
     WHERE c.position < p.high_watermark
       AND (l.cursor_id IS NULL OR w.heartbeat_detected_at < w.heartbeat_deadline);

-- ========================================================
-- VIEW: leased_cursors_view
-- List active leases. Normal use will filter by worker_id
CREATE OR REPLACE ALGORITHM = MERGE VIEW leased_cursors_view AS
    SELECT c.*,
           w.id AS worker_id,
           l.cursor_id AS lease_id,
           l.acquired_at
      FROM workers w
      JOIN leases l ON w.id = l.worker_id
      JOIN cursors c ON l.cursor_id = c.id
     WHERE w.heartbeat_detected_at < w.heartbeat_deadline
       AND l.state = 'ACTIVE'
  ORDER BY worker_id, c.id;
