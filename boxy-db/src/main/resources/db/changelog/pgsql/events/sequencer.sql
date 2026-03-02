-- PostgreSQL Sequencer Scheduling
--
-- PostgreSQL does not have a built-in event scheduler like MySQL.
-- Options for scheduling the sequencer:
--
-- 1. **pg_cron Extension** (recommended for DB-level scheduling):
--    - Install extension: CREATE EXTENSION IF NOT EXISTS pg_cron;
--    - Schedule job: SELECT cron.schedule('sequencer-job', '*/10 * * * *', 'SELECT sp_sequencer__run()');
--    - Unschedule: SELECT cron.unschedule('sequencer-job');
--
-- 2. **Application-Level Scheduling** (recommended for production):
--    - Use Spring Scheduler or Quartz with @Scheduled or CronTrigger
--    - Call stored procedure via JDBC: connection.prepareCall("{CALL sp_sequencer__run()}").execute();
--    - Provides centralized visibility and error handling
--    - Allows graceful degradation if DB is slow
--
-- This file is a placeholder; actual scheduling should be configured
-- in the application or via pg_cron administration panel.

-- Placeholder stored procedure wrapper
CREATE OR REPLACE FUNCTION sp_sequencer__run()
    LANGUAGE plpgsql
AS $$
DECLARE
    v_last_run TIMESTAMP(3);
    v_now TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3);
    v_interval_sec INT;
BEGIN
    BEGIN
        -- Read configurable interval
        SELECT COALESCE(MAX((config_value)::INT), 60)
          INTO v_interval_sec
          FROM boxy_config
         WHERE config_key = 'sequencer.interval.seconds';

        -- Check if enough time has passed since last run
        SELECT config_value::TIMESTAMP(3)
          INTO v_last_run
          FROM boxy_config
         WHERE config_key = 'sequencer.last_run'
           AND config_value IS NOT NULL AND config_value <> '';

        IF v_last_run IS NOT NULL AND (v_now - v_last_run) < (v_interval_sec || ' seconds')::INTERVAL THEN
            -- Interval not yet elapsed, skip execution
            RETURN;
        END IF;

        -- Execute sequencer loop
        CALL sp_sequence_loop(0);

        -- Update last run timestamp
        UPDATE boxy_config
           SET config_value = v_now::TEXT,
               updated_at = v_now
         WHERE config_key = 'sequencer.last_run';

    EXCEPTION WHEN OTHERS THEN
        -- Log error to background_job_errors
        INSERT INTO background_job_errors (job_name, error_code, error_msg, error_time)
        VALUES ('sequencer', SQLSTATE, SQLERRM, CURRENT_TIMESTAMP(3));
        RAISE;
    END;
END;
$$;
