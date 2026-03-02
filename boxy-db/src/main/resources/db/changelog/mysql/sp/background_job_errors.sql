-- background_job_errors: captures MySQL event scheduler failures
--
-- Items #122, #123: error logging/alerting for sequencer and consumer_gc events.
--
-- The MySQL event scheduler executes background jobs (sequencer, consumer_gc) but
-- logs errors silently to the MySQL error log (not visible to the application).
-- To make failures observable: wrap each event call in an error-capturing stored
-- procedure that inserts failures into this table. The application can then query
-- this table for alerting / monitoring.
--
-- Indexes support:
--   - Liveness checks: query recent errors by job_name and timestamp
--   - Audit trail: query all errors for a given job
CREATE TABLE IF NOT EXISTS background_job_errors (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    job_name    VARCHAR(100)  NOT NULL,
    error_code  INT           NOT NULL DEFAULT 0,
    error_msg   TEXT          NOT NULL,
    error_time  TIMESTAMP(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    INDEX idx_bgjerr__job_time (job_name, error_time)
) ENGINE=InnoDB;
