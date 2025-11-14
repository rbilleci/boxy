CREATE TABLE namespaces (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    parent_id        BIGINT NULL,
    name             VARCHAR(500)    NOT NULL,
    path             VARCHAR(4000)   NOT NULL,
    path_hash        BINARY(16)      GENERATED ALWAYS AS (UNHEX(MD5(path))) STORED,
    created_at       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    last_modified_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT u_namespaces__parent UNIQUE (parent_id, name),
    FOREIGN KEY (parent_id) REFERENCES namespaces(id) ON DELETE CASCADE,
    INDEX idx_namespaces__path_hash (path_hash)
) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_bin
    COMMENT='Stores namespaces hierarchically';

CREATE TABLE namespace_closures (
    ancestor_id   BIGINT NOT NULL,
    descendant_id BIGINT NOT NULL,
    depth         TINYINT NOT NULL,
    PRIMARY KEY (ancestor_id, descendant_id),
    FOREIGN KEY (ancestor_id) REFERENCES namespaces(id) ON DELETE CASCADE,
    FOREIGN KEY (descendant_id) REFERENCES namespaces(id) ON DELETE CASCADE,
    INDEX idx_namespace_closures_ancestor (ancestor_id),
    INDEX idx_namespace_closures_descendant (descendant_id)
) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_bin
    COMMENT='Closure table for namespaces';

CREATE TABLE topics (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    namespace_id        BIGINT NOT NULL,
    name                VARCHAR(500) NOT NULL,
    partitions          INT NOT NULL DEFAULT 1,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    last_modified_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT u_topics UNIQUE (namespace_id, name),
    FOREIGN KEY (namespace_id) REFERENCES namespaces (id),
    INDEX idx_topics__cover (namespace_id, name, partitions)
) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_bin
    COMMENT='Stores logical topics per namespace';

CREATE TABLE partitions (
    id                  BIGINT AS ((topic_id << 16) + partition_number) STORED PRIMARY KEY,
    topic_id            BIGINT NOT NULL,
    partition_number    INT NOT NULL,
    high_watermark      BIGINT DEFAULT 0 NOT NULL,
    CONSTRAINT u_partitions UNIQUE (topic_id, partition_number),
    FOREIGN KEY (topic_id) REFERENCES topics (id)
) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_bin
    COMMENT='Tracks partitions for each topic';

CREATE TABLE heartbeat_policies (
    id                           TINYINT NOT NULL PRIMARY KEY CHECK (id = 1),
    heartbeat_deadline_multiplier DOUBLE NOT NULL DEFAULT 5.0,
    heartbeat_interval_baseline   DOUBLE NOT NULL DEFAULT 3.0,
    heartbeat_interval_limit      DOUBLE NOT NULL DEFAULT 60.0,
    heartbeat_target_qps          DOUBLE NOT NULL DEFAULT 10.0
) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_bin
    COMMENT='Stores cluster-wide heartbeat configuration';

INSERT INTO heartbeat_policies (id) VALUES (1);

CREATE TABLE metrics_policies (
    id                      TINYINT NOT NULL PRIMARY KEY CHECK (id = 1),
    metrics_refresh_interval INT    NOT NULL DEFAULT 3
) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_bin
    COMMENT='Stores cluster-wide metrics configuration';

INSERT INTO metrics_policies (id) VALUES (1);

CREATE TABLE subscriptions (
    id                              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name                            VARCHAR(500) NOT NULL,
    last_modified_at                DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT u_subscriptions UNIQUE (name)
) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_bin
    COMMENT='Defines subscriptions which store consumption state';

CREATE TABLE subscription_topics (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    subscription_id     BIGINT NOT NULL,
    topic_id            BIGINT NOT NULL,
    heartbeat_interval          DOUBLE NOT NULL DEFAULT 15.0,
    active_partitions           INT NOT NULL DEFAULT 0,
    active_consumers            INT NOT NULL DEFAULT 0,
    active_consumers_weight     DOUBLE NOT NULL DEFAULT 0,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    last_modified_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT u_subscription_topics UNIQUE (subscription_id, topic_id),
    FOREIGN KEY (subscription_id) REFERENCES subscriptions (id) ON DELETE CASCADE,
    FOREIGN KEY (topic_id) REFERENCES topics (id) ON DELETE CASCADE,
    INDEX idx_subscription_topics__subscription (subscription_id, id)
) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_bin
    COMMENT='Links subscriptions to the topics they consume';

CREATE TABLE cursors (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    subscription_id       BIGINT NOT NULL,
    subscription_topic_id BIGINT NOT NULL,
    topic_id              BIGINT NOT NULL,
    partition_id          BIGINT NOT NULL,
    random_key            INT    NOT NULL,
    position              BIGINT NOT NULL DEFAULT 0,
    locked_by             VARCHAR(36) NULL,
    locked_until          DATETIME(3) NULL,
    CONSTRAINT u_cursors UNIQUE (subscription_topic_id, partition_id),
    FOREIGN KEY (subscription_id)       REFERENCES subscriptions (id) ON DELETE CASCADE,
    FOREIGN KEY (subscription_topic_id) REFERENCES subscription_topics (id) ON DELETE CASCADE,
    FOREIGN KEY (topic_id)             REFERENCES topics (id) ON DELETE CASCADE,
    FOREIGN KEY (partition_id)          REFERENCES partitions (id) ON DELETE CASCADE,
    INDEX idx_cursors__random_1 (random_key, id),
    INDEX idx_cursors__subscription_random (subscription_id, random_key, id),
    INDEX idx_cursors__lock (locked_until),
    INDEX idx_cursors__subscription_lock_random (subscription_id, locked_until, random_key, id)
) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_bin
    COMMENT='Maintains the last cursor position for each subscription/partition';

CREATE TABLE consumers (
    id                   VARCHAR(36)  PRIMARY KEY,
    subscription_id      BIGINT       NOT NULL,
    weight               DOUBLE       NOT NULL DEFAULT 1,
    heartbeat_detected_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    heartbeat_interval   DOUBLE       NOT NULL,
    heartbeat_deadline   DATETIME(3)  NOT NULL,
    FOREIGN KEY (subscription_id) REFERENCES subscriptions(id),
    INDEX idx_consumers__subscription (subscription_id),
    INDEX idx_consumers__heartbeat_detected_at (heartbeat_detected_at)
) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_bin
    COMMENT='Registered consumers per subscription, with capacity weight and heartbeat timestamp';

CREATE TABLE consumer_subscriptions (
    consumer_id VARCHAR(36) NOT NULL,
    subscription_topic_id BIGINT NOT NULL,
    topic_id BIGINT NOT NULL,
    PRIMARY KEY (consumer_id, subscription_topic_id),
    FOREIGN KEY (consumer_id) REFERENCES consumers(id) ON DELETE CASCADE,
    FOREIGN KEY (subscription_topic_id) REFERENCES subscription_topics(id) ON DELETE CASCADE,
    FOREIGN KEY (topic_id) REFERENCES topics(id) ON DELETE CASCADE,
    INDEX idx_consumer_subscriptions__subscription_topic (subscription_topic_id),
    INDEX idx_consumer_subscriptions__topic (topic_id)
) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_bin
    COMMENT='Links consumers to subscription topics they consume';

CREATE TABLE IF NOT EXISTS unprocessed_events (
    id           BIGINT PRIMARY KEY,
    partition_id BIGINT NOT NULL
) ENGINE=InnoDB
  ROW_FORMAT=COMPACT;

CREATE TABLE IF NOT EXISTS sequences (
    sequence     BIGINT AUTO_INCREMENT,
    partition_id BIGINT NOT NULL,
    event_id     BIGINT NOT NULL,
    PRIMARY KEY (sequence, partition_id),
    INDEX idx_sequences__partition_sequence (partition_id, sequence)
) ENGINE=InnoDB
  ROW_FORMAT=COMPACT;

CREATE TABLE IF NOT EXISTS events (
    id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    data LONGBLOB NOT NULL
) ENGINE=InnoDB;

CREATE TABLE topics_cache (
  path_hash    BINARY(16)    NOT NULL,
  path         VARCHAR(4000) NOT NULL,
  topic        VARCHAR(500)  NOT NULL,
  topic_id     BIGINT        NOT NULL,
  partitions   INT NOT NULL,
  PRIMARY KEY (path_hash, topic)
) ENGINE=MEMORY;
