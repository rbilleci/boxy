# Background Job Architecture and Reliability

This document describes the architecture and design decisions for Boxy's background jobs
(sequencer and consumer GC), and evaluates tradeoffs for future improvements.

**Items covered:**
- #122-123: Error logging/alerting for sequencer and consumer_gc event failures
- #127-128: Configurable intervals for background jobs
- #87-88: Health checks and liveness monitoring
- #89: Evaluation: move tmp event record deletion to a separate scheduled event thread
- #90: Evaluation: move lease cleanup to a separate thread

## Current Architecture

### Sequencer Event (`sequencer_v3` event calling `sp_sequencer__run`)

The sequencer is a MySQL event that runs every 10 seconds and calls `sp_sequencer__run()`.

**Execution flow:**
1. `sequencer` event triggers `sp_sequencer__run()` every 10 seconds
2. `sp_sequencer__run()` reads `sequencer.interval.seconds` from `boxy_config` (default: 60)
3. If the interval has elapsed, it executes `sp_sequence_loop(0)` to process unprocessed events
4. If an error occurs, it is logged to `background_job_errors` table
5. The last run timestamp is recorded in `boxy_config` for interval enforcement

**Configuration:**
```sql
-- Change effective sequencer interval to 120 seconds:
UPDATE boxy_config SET config_value = '120'
 WHERE config_key = 'sequencer.interval.seconds';

-- Force immediate execution (restart interval):
DELETE FROM boxy_config WHERE config_key = 'sequencer.last_run';
```

### Consumer GC Event (`consumer_gc_v2` event calling `sp_consumers__gc__run`)

Similar to sequencer, consumer GC is a MySQL event that runs every 10 seconds.

**Execution flow:**
1. `consumer_gc` event triggers `sp_consumers__gc__run()` every 10 seconds
2. `sp_consumers__gc__run()` reads `consumer_gc.interval.seconds` from `boxy_config` (default: 60)
3. If the interval has elapsed, it executes `sp_consumers__gc()` to clean up expired consumers
4. If an error occurs, it is logged to `background_job_errors` table
5. The last run timestamp is recorded in `boxy_config` for interval enforcement

**Configuration:**
```sql
-- Change effective consumer_gc interval to 120 seconds:
UPDATE boxy_config SET config_value = '120'
 WHERE config_key = 'consumer_gc.interval.seconds';

-- Force immediate execution (restart interval):
DELETE FROM boxy_config WHERE config_key = 'consumer_gc.last_run';
```

## Error Observability (Items #122-123)

### Background Job Errors Table

The `background_job_errors` table captures all errors that occur during background job execution:

```sql
CREATE TABLE IF NOT EXISTS background_job_errors (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    job_name    VARCHAR(100)  NOT NULL,
    error_code  INT           NOT NULL DEFAULT 0,
    error_msg   TEXT          NOT NULL,
    error_time  TIMESTAMP(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    INDEX idx_bgjerr__job_time (job_name, error_time)
) ENGINE=InnoDB;
```

**Why this approach?**
- MySQL event scheduler errors are logged to the MySQL error log, not visible to the application
- By wrapping event calls in stored procedures with error handlers, we capture errors to a table
- The application can query this table to:
  - Alert on recent errors
  - Investigate failure patterns
  - Diagnose production issues without requiring MySQL server log access

**Querying recent errors:**
```sql
-- Sequencer errors in the last 5 minutes:
SELECT * FROM background_job_errors
 WHERE job_name = 'sequencer'
   AND error_time > DATE_SUB(NOW(), INTERVAL 5 MINUTE)
 ORDER BY error_time DESC;

-- All consumer_gc errors:
SELECT * FROM background_job_errors
 WHERE job_name = 'consumer_gc'
 ORDER BY error_time DESC;
```

## Health Checks and Liveness Monitoring (Items #87-88)

The `HealthCheck` class in `boxy-core/src/main/java/boxy/core/metrics/HealthCheck.java`
provides three independent checks:

### Check 1: Database Connectivity
- Executes `SELECT 1` to verify the pool can acquire a connection
- Essential for any downstream checks

### Check 2: Sequencer Liveness
- Counts unprocessed events (`SELECT COUNT(*) FROM unprocessed_events`)
  - Threshold warning: > 10,000 unprocessed events suggests sequencer is stalled
  - Healthy backlog naturally varies with event publishing rate
- Counts recent sequencer errors (`SELECT COUNT(*) FROM background_job_errors ...`)
  - Any recent error (< 5 minutes) indicates the sequencer failed

**Liveness interpretation:**
- **Healthy:** No recent errors AND backlog < 10,000
- **Degraded:** Recent errors OR backlog > 10,000
- **Failed:** Unable to query (database connectivity issue)

### Check 3: Consumer GC Liveness
- Counts expired consumers (`SELECT COUNT(*) FROM consumers WHERE heartbeat_deadline < NOW()`)
  - Threshold warning: > 1,000 expired consumers suggests GC is stalled
  - Healthy expired consumer count varies with subscription lifecycle
- Counts recent consumer_gc errors (`SELECT COUNT(*) FROM background_job_errors ...`)
  - Any recent error (< 5 minutes) indicates the consumer_gc failed

**Liveness interpretation:**
- **Healthy:** No recent errors AND expired consumers < 1,000
- **Degraded:** Recent errors OR expired consumers > 1,000
- **Failed:** Unable to query (database connectivity issue)

## Evaluation: Separate Thread for Tmp Event Record Deletion (Item #89)

**Current approach:** Event record cleanup is done inline during `sp_sequence_loop` execution.

### Option A: Keep inline (current)
**Pros:**
- No additional scheduled thread to manage
- Cleanup happens naturally as events are processed
- No additional database connections
- Simple to reason about

**Cons:**
- Cleanup competes with sequencing for CPU and I/O
- If events expire faster than they're processed, tmp table grows
- Not visible as a separate monitored job

### Option B: Separate scheduled event thread
**Pros:**
- Isolates cleanup from sequencing performance
- Can be tuned independently (separate interval/batch size)
- Easier to monitor and alert on cleanup failures
- Cleanup can be aggressive without affecting event processing

**Cons:**
- Additional event scheduler job to manage
- Need separate liveness monitoring
- Slightly more schema (last_run tracking, error logging)
- More complex failure modes (what if sequencer is healthy but cleanup isn't?)

### Recommendation
**Keep the current inline approach (Option A)** for now:
- Cleanup is lightweight (just time-based filtering)
- Coupling with sequencer keeps related work together
- Can revisit if monitoring shows cleanup becoming a bottleneck
- If needed in future, this can be refactored without major architectural changes

---

## Evaluation: Separate Thread for Consumer Lease Cleanup (Item #90)

**Current approach:** Consumer lease cleanup is done inline during `sp_consumers__gc_v3` execution.

### Current sp_consumers__gc_v3 Flow
1. Delete consumer_leases for expired consumers in batches of 100
2. Delete consumer_leases' parent consumers in batches of 100
3. Both operations are part of the same GC execution

### Option A: Keep inline (current)
**Pros:**
- Single transactional boundary
- Consumer and its leases always stay in sync
- Simpler to reason about (no orphaned leases)
- Lower overhead (single batch delete)
- No additional scheduled job

**Cons:**
- Lease cleanup competes with consumer deletion for I/O
- If leases are numerous, cleanup becomes expensive
- Not separately observable

### Option B: Separate scheduled cleanup thread
**Pros:**
- Isolates lease deletion from consumer deletion
- Can be more aggressive with lease batch size
- Lease cleanup can happen on a different schedule than consumer cleanup
- Easier to tune independently

**Cons:**
- Risk of orphaned consumer_leases if cleanup fails
- Need foreign key constraints to prevent orphans
- More complex failure mode (what if consumers are deleted but leases aren't?)
- Additional event scheduler job and monitoring
- More code to maintain

### Recommendation
**Keep the current approach (Option A)** for now:
- Consumer and lease deletion are logically coupled
- Current batching (100 at a time) is reasonable
- Lease cleanup is just a DELETE JOIN, not expensive
- Separate thread introduces orphaning risk without clear benefit
- The current approach keeps invariants simple (a consumer either exists with its leases, or not at all)

---

## Future Enhancements

### Configurable Cleanup Aggressiveness
Instead of separate threads, we could add configurable batch sizes and intervals:

```sql
-- More aggressive cleaning:
UPDATE boxy_config SET config_value = '500' WHERE config_key = 'consumer_gc.batch.size';
UPDATE boxy_config SET config_value = '30' WHERE config_key = 'consumer_gc.interval.seconds';

-- Or for sequencer:
UPDATE boxy_config SET config_value = '5000' WHERE config_key = 'sequencer.batch.size';
UPDATE boxy_config SET config_value = '30' WHERE config_key = 'sequencer.interval.seconds';
```

This gives fine-grained control without adding architectural complexity.

### Enhanced Monitoring
Future work could add:
- Metrics emission (e.g., background_job_errors count, last_run timestamp)
- Automatic alerting (e.g., alert if no successful run in 10 minutes)
- Dashboard visualization of job health over time
- Per-job configurable SLOs and breach notifications

---

## See Also
- `sp_sequencer__run()` — wrapper procedure with error capture and interval enforcement
- `sp_consumers__gc__run()` — wrapper procedure with error capture and interval enforcement
- `HealthCheck` class — health check utility for production monitoring
- `PRODUCTION_READINESS.md` — overall production readiness checklist
