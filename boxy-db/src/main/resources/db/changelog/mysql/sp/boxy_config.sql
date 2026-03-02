-- boxy_config: runtime-configurable parameters for stored procedures.
--
-- Items #90, #92, #93: Make consumer lease lock duration, sequencer batch size,
-- and default heartbeat interval configurable without redeploying stored procedures.
--
-- Items #99, #100: Make maximum event payload size and batch size configurable.
--
-- Stored procedures that read from this table:
--   sp_events__poll v4       — reads lease.lock.seconds       (item #90)
--   sp_sequence_loop v4      — reads sequencer.batch.size     (item #92)
--   sp_subscriptions__subscribe v3 — reads heartbeat.interval.seconds (item #93)
--   sp_events__publish v3    — reads max.event.payload.bytes  (item #99)
--   sp_events__publish_multi v4 — reads max.batch.size        (item #100)
--
-- To override a default:
--   UPDATE boxy_config SET config_value = '5' WHERE config_key = 'lease.lock.seconds';
CREATE TABLE IF NOT EXISTS boxy_config (
    config_key   VARCHAR(100)  NOT NULL,
    config_value VARCHAR(500)  NOT NULL,
    description  VARCHAR(1000) NOT NULL DEFAULT '',
    updated_at   TIMESTAMP(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                               ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (config_key)
) ENGINE = InnoDB;

-- Seed defaults (INSERT IGNORE to allow partial re-runs without overwriting customizations)
INSERT IGNORE INTO boxy_config (config_key, config_value, description) VALUES
    ('lease.lock.seconds',           '3',       'Consumer lease lock duration in seconds (sp_events__poll step 5)'),
    ('sequencer.batch.size',         '1000',    'Default sequencer iteration batch size passed to sp_sequence_loop'),
    ('heartbeat.interval.seconds',   '15',      'Default heartbeat_interval for new subscription_topics rows'),
    ('max.event.payload.bytes',      '1048576', 'Maximum event payload size in bytes (sp_events__publish validation)'),
    ('max.batch.size',               '1000',    'Maximum number of events per sp_events__publish_multi call');
