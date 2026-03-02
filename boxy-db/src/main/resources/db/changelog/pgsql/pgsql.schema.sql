-- PostgreSQL Schema for Boxy
-- Ported from MySQL, with PostgreSQL-specific syntax

CREATE TABLE namespaces (
    id               BIGSERIAL PRIMARY KEY,
    parent_id        BIGINT NULL,
    name             VARCHAR(500)    NOT NULL,
    path             VARCHAR(4000)   NOT NULL,
    path_hash        BYTEA           GENERATED ALWAYS AS (DECODE(MD5(path), 'hex')) STORED,
    created_at       TIMESTAMP(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    last_modified_at TIMESTAMP(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT u_namespaces__parent UNIQUE (parent_id, name),
    FOREIGN KEY (parent_id) REFERENCES namespaces(id) ON DELETE CASCADE,
    CHECK (id IS NOT NULL)
);

CREATE INDEX idx_namespaces__path_hash ON namespaces (path_hash);

CREATE TABLE namespace_closures (
    ancestor_id   BIGINT NOT NULL,
    descendant_id BIGINT NOT NULL,
    depth         SMALLINT NOT NULL,
    PRIMARY KEY (ancestor_id, descendant_id),
    FOREIGN KEY (ancestor_id) REFERENCES namespaces(id) ON DELETE CASCADE,
    FOREIGN KEY (descendant_id) REFERENCES namespaces(id) ON DELETE CASCADE
);

CREATE INDEX idx_namespace_closures_ancestor ON namespace_closures (ancestor_id);
CREATE INDEX idx_namespace_closures_descendant ON namespace_closures (descendant_id);

CREATE TABLE topics (
    id                  BIGSERIAL PRIMARY KEY,
    namespace_id        BIGINT NOT NULL,
    name                VARCHAR(500) NOT NULL,
    partitions          INT NOT NULL DEFAULT 1,
    created_at          TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    last_modified_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT u_topics UNIQUE (namespace_id, name),
    FOREIGN KEY (namespace_id) REFERENCES namespaces (id)
);

CREATE INDEX idx_topics__cover ON topics (namespace_id, name, partitions);

CREATE TABLE partitions (
    id                  BIGINT PRIMARY KEY,
    topic_id            BIGINT NOT NULL,
    partition_number    INT NOT NULL,
    high_watermark      BIGINT DEFAULT 0 NOT NULL,
    CONSTRAINT u_partitions UNIQUE (topic_id, partition_number),
    FOREIGN KEY (topic_id) REFERENCES topics (id)
);

CREATE TABLE subscriptions (
    id                              BIGSERIAL PRIMARY KEY,
    name                            VARCHAR(500) NOT NULL,
    last_modified_at                TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT u_subscriptions UNIQUE (name)
);

CREATE TABLE subscription_topics (
    id                  BIGSERIAL PRIMARY KEY,
    subscription_id     BIGINT NOT NULL,
    topic_id            BIGINT NOT NULL,
    heartbeat_interval  DOUBLE PRECISION NOT NULL DEFAULT 15.0,
    active_partitions   INT NOT NULL DEFAULT 0,
    active_consumers    INT NOT NULL DEFAULT 0,
    created_at          TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    last_modified_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT u_subscription_topics UNIQUE (subscription_id, topic_id),
    FOREIGN KEY (subscription_id) REFERENCES subscriptions (id) ON DELETE CASCADE,
    FOREIGN KEY (topic_id) REFERENCES topics (id) ON DELETE CASCADE
);

CREATE INDEX idx_subscription_topics__subscription ON subscription_topics (subscription_id, id);

CREATE TABLE cursors (
    id               BIGSERIAL PRIMARY KEY,
    subscription_id       BIGINT NOT NULL,
    subscription_topic_id BIGINT NOT NULL,
    topic_id              BIGINT NOT NULL,
    partition_id          BIGINT NOT NULL,
    random_key            INT    NOT NULL,
    position              BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT u_cursors UNIQUE (subscription_topic_id, partition_id),
    FOREIGN KEY (subscription_id)       REFERENCES subscriptions (id) ON DELETE CASCADE,
    FOREIGN KEY (subscription_topic_id) REFERENCES subscription_topics (id) ON DELETE CASCADE,
    FOREIGN KEY (topic_id)             REFERENCES topics (id) ON DELETE CASCADE,
    FOREIGN KEY (partition_id)          REFERENCES partitions (id) ON DELETE CASCADE
);

CREATE INDEX idx_cursors__random_1 ON cursors (random_key, id);
CREATE INDEX idx_cursors__subscription_random ON cursors (subscription_id, random_key, id);

CREATE TABLE consumers (
    id                   VARCHAR(36)  PRIMARY KEY,
    subscription_id      BIGINT       NOT NULL,
    heartbeat_detected_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    heartbeat_interval   DOUBLE PRECISION NOT NULL,
    heartbeat_deadline   TIMESTAMP(3)  NOT NULL,
    topic_ids            JSONB         NOT NULL DEFAULT '[]'::jsonb,
    FOREIGN KEY (subscription_id) REFERENCES subscriptions(id),
    CHECK (id IS NOT NULL)
);

CREATE INDEX idx_consumers__subscription ON consumers (subscription_id);
CREATE INDEX idx_consumers__heartbeat_detected_at ON consumers (heartbeat_detected_at);

CREATE TABLE consumer_leases (
    id           BIGSERIAL PRIMARY KEY,
    consumer_id  VARCHAR(36) NULL,
    cursor_id    BIGINT      NOT NULL,
    locked_until TIMESTAMP(3) NULL,
    last_read_position BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT u_consumer_leases__cursor UNIQUE (cursor_id),
    FOREIGN KEY (cursor_id)   REFERENCES cursors (id) ON DELETE CASCADE,
    FOREIGN KEY (consumer_id) REFERENCES consumers (id) ON DELETE CASCADE
);

CREATE INDEX idx_consumer_leases__lock ON consumer_leases (locked_until);

CREATE TABLE unprocessed_events (
    id           BIGINT PRIMARY KEY,
    partition_id BIGINT NOT NULL
);

CREATE TABLE sequences (
    sequence     BIGSERIAL,
    partition_id BIGINT NOT NULL,
    event_id     BIGINT NOT NULL,
    PRIMARY KEY (sequence, partition_id)
);

CREATE INDEX idx_sequences__partition_sequence ON sequences (partition_id, sequence);

CREATE TABLE events (
    id   BIGSERIAL PRIMARY KEY,
    data BYTEA NOT NULL
);

-- PostgreSQL: no MEMORY tables; use regular table for topics_cache
-- Note: In production, consider using Redis or another cache instead
CREATE TABLE topics_cache (
  path_hash    BYTEA         NOT NULL,
  path         VARCHAR(4000) NOT NULL,
  topic        VARCHAR(500)  NOT NULL,
  topic_id     BIGINT        NOT NULL,
  partitions   INT NOT NULL,
  PRIMARY KEY (path_hash, topic)
);

-- Configuration table for runtime-configurable parameters
CREATE TABLE IF NOT EXISTS boxy_config (
    id           BIGSERIAL PRIMARY KEY,
    config_key   VARCHAR(255) NOT NULL UNIQUE,
    config_value TEXT,
    description  TEXT,
    created_at   TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3),
    updated_at   TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3)
);

-- Background job error logging
CREATE TABLE IF NOT EXISTS background_job_errors (
    id          BIGSERIAL PRIMARY KEY,
    job_name    VARCHAR(100)  NOT NULL,
    error_code  INT           NOT NULL DEFAULT 0,
    error_msg   TEXT          NOT NULL,
    error_time  TIMESTAMP(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
);

CREATE INDEX idx_bgjerr__job_time ON background_job_errors (job_name, error_time);

-- Seed default configuration values
INSERT INTO boxy_config (config_key, config_value, description) VALUES
    ('lease.lock.seconds', '3', 'Consumer lease lock duration in seconds'),
    ('sequencer.batch.size', '1000', 'Default batch size for sequencer'),
    ('heartbeat.interval.seconds', '15', 'Default heartbeat interval in seconds'),
    ('max.event.payload.bytes', '1048576', 'Maximum event payload size in bytes (1 MiB)'),
    ('max.batch.size', '1000', 'Maximum batch size for publish_multi'),
    ('sequencer.interval.seconds', '60', 'Minimum interval between sequencer executions in seconds'),
    ('consumer_gc.interval.seconds', '60', 'Minimum interval between consumer_gc executions in seconds'),
    ('sequencer.last_run', '', 'Timestamp of last sequencer execution'),
    ('consumer_gc.last_run', '', 'Timestamp of last consumer_gc execution')
ON CONFLICT (config_key) DO NOTHING;
