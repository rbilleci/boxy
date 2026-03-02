-- consumer_gc event v2 — calls sp_consumers__gc__run() which wraps sp_consumers__gc with:
--   - error capture to background_job_errors table
--   - configurable minimum interval (default 60 seconds)
--   - liveness tracking via last_run timestamp
--
-- Items #123 (error logging), #128 (configurable interval)
--
-- The event runs frequently (every 10 seconds), but sp_consumers__gc__run skips
-- if the configured interval has not yet elapsed. This allows fine-grained
-- control over the effective GC frequency via boxy_config:
--   UPDATE boxy_config SET config_value = '120'
--    WHERE config_key = 'consumer_gc.interval.seconds';
DROP EVENT IF EXISTS consumer_gc;
CREATE EVENT consumer_gc
    ON SCHEDULE EVERY 10 SECOND
    ON COMPLETION PRESERVE
    ENABLE
    DO CALL sp_consumers__gc__run();
