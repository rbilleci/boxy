-- sp_sequencer__run: wrapper around sp_sequence_loop with error capture,
-- configurable minimum interval, and liveness tracking.
--
-- Items #122 (error logging for sequencer event failures)
-- Items #127 (configurable interval)
--
-- The MySQL event scheduler executes this procedure on a frequent interval
-- (e.g., every 10 seconds). This procedure:
--   1. Reads sequencer.interval.seconds from boxy_config (default 60)
--   2. Reads sequencer.last_run timestamp from boxy_config
--   3. Skips execution if less than the configured interval has elapsed
--   4. Updates sequencer.last_run before executing the real sequencer
--   5. Calls sp_sequence_loop(0) to process event batches
--   6. Logs any errors to background_job_errors table
--
-- To change the effective interval without redeploying the event:
--   UPDATE boxy_config SET config_value = '120'
--    WHERE config_key = 'sequencer.interval.seconds';
--
-- To force immediate execution (restart the interval):
--   DELETE FROM boxy_config WHERE config_key = 'sequencer.last_run';
DROP PROCEDURE IF EXISTS sp_sequencer__run;
CREATE PROCEDURE sp_sequencer__run()
sp_sequencer__run_label: BEGIN
    DECLARE v_interval_secs INT DEFAULT 60;
    DECLARE v_last_run       TIMESTAMP(3);
    DECLARE v_error_code     INT;
    DECLARE v_error_msg      TEXT;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        GET DIAGNOSTICS CONDITION 1
            v_error_code = MYSQL_ERRNO,
            v_error_msg = MESSAGE_TEXT;
        INSERT INTO background_job_errors (job_name, error_code, error_msg)
             VALUES ('sequencer', v_error_code, v_error_msg);
        RESIGNAL;
    END;

    -- Read configurable interval from boxy_config (default 60 seconds)
    BEGIN
        DECLARE sql_error INT;
        DECLARE CONTINUE HANDLER FOR SQLEXCEPTION
        BEGIN
            SET sql_error = 1;
            SET v_interval_secs = 60;
        END;
        SELECT CAST(config_value AS UNSIGNED)
          INTO v_interval_secs
          FROM boxy_config
         WHERE config_key = 'sequencer.interval.seconds'
         LIMIT 1;
        IF sql_error = 1 THEN
            SET v_interval_secs = 60;
        END IF;
    END;

    -- Read last run timestamp from boxy_config
    BEGIN
        DECLARE sql_error INT;
        DECLARE CONTINUE HANDLER FOR SQLEXCEPTION
        BEGIN
            SET sql_error = 1;
            SET v_last_run = NULL;
        END;
        SELECT CAST(config_value AS DATETIME(3))
          INTO v_last_run
          FROM boxy_config
         WHERE config_key = 'sequencer.last_run'
         LIMIT 1;
        IF sql_error = 1 THEN
            SET v_last_run = NULL;
        END IF;
    END;

    -- Skip execution if run too recently (interval not yet elapsed)
    IF v_last_run IS NOT NULL
       AND TIMESTAMPDIFF(SECOND, v_last_run, CURRENT_TIMESTAMP(3)) < v_interval_secs THEN
        LEAVE sp_sequencer__run_label;
    END IF;

    -- Update last run timestamp before execution
    INSERT INTO boxy_config (config_key, config_value, description)
         VALUES ('sequencer.last_run', DATE_FORMAT(CURRENT_TIMESTAMP(3), '%Y-%m-%d %H:%i:%s.%f'), 'Timestamp of last sequencer run')
    ON DUPLICATE KEY UPDATE config_value = VALUES(config_value);

    -- Execute the actual sequencer loop
    CALL sp_sequence_loop(0);
END sp_sequencer__run_label;
