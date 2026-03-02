# Event Retention and Cleanup Strategy

This guide documents Boxy's event lifecycle management, retention policies, and cleanup procedures.

## Overview

Boxy is designed for **transient event streaming**; old events are cleaned up once they have been consumed by all subscribers. The system provides a configurable retention period and automatic cleanup via stored procedures.

### Key Properties

- **Events are never deleted until explicitly cleaned up** — once published, an event remains in the `events` table until `sp_events__cleanup()` is called.
- **Cleanup is asynchronous and manual** — the operator is responsible for scheduling cleanup based on business requirements.
- **Cleanup is batched** — the procedure can be called repeatedly with configurable batch sizes to avoid large transactions.
- **Sequence records persist indefinitely** — the `sequences` table is the source of truth for event ordering and is never cleaned up.

---

## Retention Configuration

### Configurable Retention Period

The retention period is stored in the `boxy_config` table:

```sql
-- Check current retention policy
SELECT config_key, config_value FROM boxy_config 
WHERE config_key = 'events.retain.days';
```

**Default**: `30` days (events older than 30 days can be cleaned up).

### Updating the Retention Period

Change the retention period at runtime (takes effect on the next cleanup call):

```sql
-- Increase retention to 60 days
UPDATE boxy_config 
SET config_value = '60' 
WHERE config_key = 'events.retain.days';
```

> **Note**: Changing the retention period does NOT retroactively delete events.
> Existing events are cleaned up based on the `sp_events__cleanup()` call time,
> not the configuration update time.

---

## Cleanup Procedures

### sp_events__cleanup

The `sp_events__cleanup` stored procedure removes old events in batches.

**Signature**:
```sql
CALL sp_events__cleanup(
    p_cutoff_time DATETIME(3),  -- delete events.created_at < this timestamp
    p_batch_size INT            -- max events to delete per call (0 = default 10000)
);
```

**Behavior**:
1. Finds up to `p_batch_size` events where `created_at < p_cutoff_time`.
2. Deletes the events from the `events` table.
3. The `sequences` table is NOT modified (preserves ordering history).
4. Returns `success` or raises a SIGNAL error if validation fails.

### Manual Cleanup Examples

#### Example 1: Daily Cleanup of Events Older Than 30 Days

```sql
-- Run this daily (e.g. via cron or a scheduled procedure)
CALL sp_events__cleanup(
    DATE_SUB(NOW(), INTERVAL 30 DAY),  -- cutoff_time
    10000                              -- batch_size
);

-- Check how many events were deleted
SELECT ROW_COUNT() AS deleted_count;
```

#### Example 2: Aggressive Cleanup (Short Retention)

For high-throughput systems where storage is a constraint, reduce retention to 7 days:

```sql
-- Update retention policy
UPDATE boxy_config 
SET config_value = '7' 
WHERE config_key = 'events.retain.days';

-- Cleanup every 6 hours
CALL sp_events__cleanup(
    DATE_SUB(NOW(), INTERVAL 7 DAY),
    50000  -- larger batch for aggressive cleanup
);
```

#### Example 3: Conservative Cleanup (Long Retention)

For compliance or auditing requirements, extend retention to 90 days:

```sql
-- Update retention policy
UPDATE boxy_config 
SET config_value = '90' 
WHERE config_key = 'events.retain.days';

-- Cleanup once per week
CALL sp_events__cleanup(
    DATE_SUB(NOW(), INTERVAL 90 DAY),
    5000  -- smaller batch to reduce lock contention
);
```

#### Example 4: Batch Cleanup Loop

To clean up a large backlog of old events without overwhelming the database, use multiple calls:

```sql
-- Clean up events older than 60 days, in batches of 5000
DELIMITER $$
CREATE PROCEDURE cleanup_in_batches(
    p_cutoff_date DATE,
    p_batch_size INT
)
BEGIN
    DECLARE v_total_deleted INT DEFAULT 0;
    DECLARE v_batch_deleted INT;

    cleanup_loop: LOOP
        CALL sp_events__cleanup(
            CONCAT(p_cutoff_date, ' 23:59:59'),
            p_batch_size
        );

        SET v_batch_deleted = ROW_COUNT();
        SET v_total_deleted = v_total_deleted + v_batch_deleted;

        IF v_batch_deleted < p_batch_size THEN
            -- No more events to delete
            LEAVE cleanup_loop;
        END IF;

        -- Brief sleep to allow other connections through
        DO SLEEP(1);
    END LOOP;

    SELECT CONCAT('Deleted ', v_total_deleted, ' events') AS status;
END$$
DELIMITER ;

-- Run it
CALL cleanup_in_batches('2025-12-01', 5000);
```

---

## Scheduled Cleanup (MySQL Event)

To automate cleanup, create a MySQL event that runs on a schedule:

### One-Time Setup

```sql
-- Create an event to run cleanup daily at 2 AM UTC
CREATE EVENT cleanup_daily_at_2am
ON SCHEDULE EVERY 1 DAY
STARTS '2026-03-03 02:00:00 UTC'
DO
BEGIN
    DECLARE v_cutoff_time DATETIME(3);
    DECLARE v_retention_days INT DEFAULT 30;

    -- Read retention policy from boxy_config
    SELECT CAST(COALESCE(MAX(config_value), '30') AS UNSIGNED)
    INTO v_retention_days
    FROM boxy_config
    WHERE config_key = 'events.retain.days';

    -- Cleanup events older than the retention window
    SET v_cutoff_time = DATE_SUB(NOW(), INTERVAL v_retention_days DAY);
    
    CALL sp_events__cleanup(v_cutoff_time, 10000);
END;
```

### Enable/Disable the Event

```sql
-- Enable the event
ALTER EVENT cleanup_daily_at_2am ENABLE;

-- Disable the event (for maintenance)
ALTER EVENT cleanup_daily_at_2am DISABLE;

-- Check event status
SHOW EVENTS WHERE name = 'cleanup_daily_at_2am' \G
```

### Monitor Scheduled Cleanup

```sql
-- Check if the event ran successfully
SELECT id, event_name, event_db, status, last_executed, last_altered
FROM information_schema.EVENTS
WHERE event_name = 'cleanup_daily_at_2am';

-- Check for cleanup errors in background_job_errors
-- (if the cleanup event throws an error)
SELECT id, job_name, error_time, error_message
FROM background_job_errors
WHERE job_name = 'cleanup'
ORDER BY error_time DESC LIMIT 10;
```

---

## Impact on Sequence Positions

### Important: Sequences are Immutable

Cleanup only deletes from the `events` table; the `sequences` table is never modified.

This means:
- **Cursor positions remain valid**: A cursor at sequence position 42 can still read event 42 if it exists.
- **Deleted events cannot be re-read**: If event 42 is deleted, polling will skip it (the event doesn't exist, so the sequence entry points to nothing).
- **No position reassignment**: Cleanup does NOT renumber sequences.

### Example: Event Lifecycle

```
Published:  event_id=100, sequence=42, partition_id=5, created_at='2025-01-01 10:00:00'
Consumed:   consumer polls and reads event 100
Committed:  cursor advances to position=42
Cleanup:    events older than 30 days deleted; event_id=100 is deleted
Impact:     cursor is still at position=42, but if a new consumer polls from <42, 
            event 100 will not be delivered (doesn't exist)
```

---

## Monitoring Retention Lag

### Retention Lag Metric

The "retention lag" is the age of the oldest unconsumed event. Track this to ensure cleanup doesn't prematurely delete events that haven't been consumed:

```sql
-- Check retention lag (age of oldest undeleted event)
SELECT 
    MIN(e.created_at) AS oldest_event_time,
    TIMESTAMPDIFF(DAY, MIN(e.created_at), NOW()) AS age_in_days
FROM events e;

-- If age_in_days > retention days, cleanup is keeping up
-- If age_in_days <= retention days, events are building up
```

### Gauge: Create a Micrometer Gauge

Register a gauge to track retention lag in your monitoring system:

```java
Gauge.builder("boxy.retention.lag.days", dataSource, ds -> {
    try (var conn = ds.getConnection();
         var ps = conn.prepareStatement(
             "SELECT TIMESTAMPDIFF(DAY, MIN(created_at), NOW()) FROM events");
         var rs = ps.executeQuery()) {
        return rs.next() ? rs.getLong(1) : 0L;
    } catch (SQLException e) {
        return -1L;
    }
})
.description("Age in days of the oldest event in the database")
.register(BoxyMeterRegistry.get());
```

### Alerting: Retention Lag Growing

Create an alert if the oldest event is approaching the retention window:

```promql
# Alert if the oldest event is older than 25 days (5 days before deletion window)
boxy_retention_lag_days > 25
```

---

## Operational Considerations

### 1. Cleanup During High Load

Cleanup queries can be I/O-intensive. Run cleanup during off-peak hours:

```sql
-- Schedule cleanup for low-traffic windows (e.g. 2–4 AM UTC)
CREATE EVENT cleanup_daily_at_2am
ON SCHEDULE EVERY 1 DAY
STARTS '2026-03-03 02:00:00 UTC'
DO
    CALL sp_events__cleanup(DATE_SUB(NOW(), INTERVAL 30 DAY), 10000);
```

### 2. Batch Size Tuning

- **Small batches** (5000): Reduce lock contention; cleanup is slower overall.
- **Large batches** (50000): Faster cleanup; may block other operations.
- **Start conservative**: Use batch size 10000 and increase if cleanup completes in < 5 seconds.

### 3. Backup Before Large Cleanup

If deleting millions of events, back up the database first:

```bash
mysqldump -h $DB_HOST -u $DB_USER -p$DB_PASSWORD \
  --single-transaction \
  events_db > backup-before-cleanup-$(date +%Y%m%d).sql
```

### 4. Transaction Handling

Each cleanup call is a single transaction:

```sql
-- If cleanup is interrupted, it rolls back entirely (no partial deletes)
CALL sp_events__cleanup(DATE_SUB(NOW(), INTERVAL 30 DAY), 10000);
-- If killed mid-execution, the transaction rolls back automatically
```

### 5. Monitoring Cleanup Performance

```sql
-- Check cleanup duration
SET @start = CURRENT_TIMESTAMP(3);
CALL sp_events__cleanup(DATE_SUB(NOW(), INTERVAL 30 DAY), 10000);
SET @end = CURRENT_TIMESTAMP(3);
SELECT TIMESTAMPDIFF(MILLISECOND, @start, @end) AS cleanup_duration_ms;
```

---

## Compliance and Compliance Hold

### Legal Hold

If an event is subject to legal hold, ensure it is not deleted even if beyond the retention window:

```sql
-- Option 1: Extend retention_days beyond the legal hold period
UPDATE boxy_config 
SET config_value = '1095'  -- 3 years
WHERE config_key = 'events.retain.days';

-- Option 2: Add a legal_hold_until column to events table (custom extension)
-- ALTER TABLE events ADD COLUMN legal_hold_until DATETIME(3) NULL;
-- Modify sp_events__cleanup to exclude events with legal_hold_until > NOW()
```

### Retention Verification

Ensure cleanup does not delete events still needed:

```sql
-- Count events by age bracket
SELECT 
    CASE 
        WHEN created_at > DATE_SUB(NOW(), INTERVAL 7 DAY) THEN '0-7 days'
        WHEN created_at > DATE_SUB(NOW(), INTERVAL 30 DAY) THEN '7-30 days'
        WHEN created_at > DATE_SUB(NOW(), INTERVAL 90 DAY) THEN '30-90 days'
        ELSE '> 90 days'
    END AS age_bracket,
    COUNT(*) AS event_count
FROM events
GROUP BY age_bracket
ORDER BY created_at DESC;
```

---

## Troubleshooting

### Cleanup Hangs or Times Out

**Symptom**: `CALL sp_events__cleanup()` appears to hang indefinitely.

**Diagnosis**:
```sql
-- Check if a cleanup is running
SHOW PROCESSLIST WHERE command = 'Query' AND info LIKE '%sp_events__cleanup%';

-- Check for locks
SHOW OPEN TABLES WHERE in_use > 0 AND name = 'events';

-- Check for long-running transactions
SELECT * FROM INFORMATION_SCHEMA.INNODB_TRX 
WHERE trx_started < DATE_SUB(NOW(), INTERVAL 1 MINUTE);
```

**Resolution**:
1. Reduce batch size: `CALL sp_events__cleanup(..., 1000)` instead of 10000.
2. Run cleanup during off-peak hours to reduce contention.
3. Kill the hanging cleanup: `KILL <thread_id>` (safe; transaction rolls back).

### Cleanup Deletes Too Many Events

**Symptom**: Consumer misses events because they were cleaned up prematurely.

**Diagnosis**:
```sql
-- Check if retention policy is too aggressive
SELECT config_value FROM boxy_config 
WHERE config_key = 'events.retain.days';

-- Check cleanup schedule
SHOW EVENTS WHERE event_name LIKE '%cleanup%' \G
```

**Resolution**:
1. Increase retention days: `UPDATE boxy_config SET config_value = '60' WHERE config_key = 'events.retain.days'`.
2. Monitor all consumers to ensure they commit positions at least once per retention window.

### Disk Space Growing

**Symptom**: Disk usage for the `events` table grows unbounded; cleanup isn't keeping up.

**Diagnosis**:
```sql
-- Check table size
SELECT 
    table_name,
    ROUND(((data_length + index_length) / 1024 / 1024), 2) AS size_mb
FROM information_schema.TABLES
WHERE table_schema = 'events_db' AND table_name = 'events'
ORDER BY size_mb DESC;

-- Check events by age
SELECT 
    COUNT(*) AS total_events,
    MIN(created_at) AS oldest_event,
    TIMESTAMPDIFF(DAY, MIN(created_at), NOW()) AS oldest_age_days
FROM events;
```

**Resolution**:
1. Run cleanup more frequently: create a 6-hourly event instead of daily.
2. Increase batch size to delete more per call: `CALL sp_events__cleanup(..., 50000)`.
3. Reduce retention window: `UPDATE boxy_config SET config_value = '14' WHERE config_key = 'events.retain.days'`.

---

## Related Documentation

- [Operational Runbook](runbook.md) — Maintenance tasks and monitoring
- [Configuration Guide](configuration.md) — Retention period configuration
- [Database Schema](schema.md) — events and sequences table structure
