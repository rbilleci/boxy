-- boxy_config table configuration values
-- This is already created in pgsql.schema.sql, but we're including INSERT statements here
-- for completeness (following MySQL structure)

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
