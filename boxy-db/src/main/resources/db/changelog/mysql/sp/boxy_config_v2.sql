-- boxy_config updates for changeset 13 (background job reliability items #122-#128)
--
-- Add configuration entries for:
--   - sequencer.interval.seconds: configurable interval for sequencer execution (default 60s)
--   - consumer_gc.interval.seconds: configurable interval for consumer_gc execution (default 60s)
--   - sequencer.last_run: timestamp of last sequencer run (tracking for interval enforcement)
--   - consumer_gc.last_run: timestamp of last consumer_gc run (tracking for interval enforcement)
INSERT IGNORE INTO boxy_config (config_key, config_value, description) VALUES
    ('sequencer.interval.seconds',     '60',      'Minimum interval between sequencer executions in seconds (sp_sequencer__run)'),
    ('consumer_gc.interval.seconds',   '60',      'Minimum interval between consumer_gc executions in seconds (sp_consumers__gc__run)'),
    ('sequencer.last_run',             '',        'Timestamp of last sequencer execution (set by sp_sequencer__run, leave empty to run immediately)'),
    ('consumer_gc.last_run',           '',        'Timestamp of last consumer_gc execution (set by sp_consumers__gc__run, leave empty to run immediately)');
