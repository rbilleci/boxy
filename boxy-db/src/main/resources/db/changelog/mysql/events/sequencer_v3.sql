-- sequencer event v3 — calls sp_sequencer__run() which wraps sp_sequence_loop with:
--   - error capture to background_job_errors table
--   - configurable minimum interval (default 60 seconds)
--   - liveness tracking via last_run timestamp
--
-- Items #122 (error logging), #127 (configurable interval)
--
-- The event runs frequently (every 10 seconds), but sp_sequencer__run skips
-- if the configured interval has not yet elapsed. This allows fine-grained
-- control over the effective sequencer frequency via boxy_config:
--   UPDATE boxy_config SET config_value = '120'
--    WHERE config_key = 'sequencer.interval.seconds';
DROP EVENT IF EXISTS sequencer;
CREATE EVENT sequencer
    ON SCHEDULE EVERY 10 SECOND
    ON COMPLETION PRESERVE
    ENABLE
    DO CALL sp_sequencer__run();
