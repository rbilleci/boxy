# Boxy Operational Runbook

This runbook provides operational procedures for running, scaling, monitoring, troubleshooting, and maintaining Boxy in production.

## Table of Contents

- [System Architecture Overview](#system-architecture-overview)
- [Startup and Shutdown Procedures](#startup-and-shutdown-procedures)
- [Scaling Consumers Horizontally](#scaling-consumers-horizontally)
- [Monitoring Key Metrics](#monitoring-key-metrics)
- [Troubleshooting Guide](#troubleshooting-guide)
- [Maintenance Tasks](#maintenance-tasks)
- [Alerting Thresholds and Escalation](#alerting-thresholds-and-escalation)

---

## System Architecture Overview

Boxy is a distributed event streaming system built on MySQL and Java. The architecture has three main tiers:

### 1. Producers

Applications publish events via `EventRepository.publish(path, topic, key, data)` or `EventRepository.publishBatch(requests)`.

- Single events are inserted into the `events` table and immediately queued to `unprocessed_events` for sequencing.
- Batch publishes are transactional: all events are inserted in a single `sp_events__publish_multi` call.
- Payload size is validated against `boxy_config: max.event.payload.bytes` (default 1 MiB).

### 2. Sequencer (MySQL Event)

The MySQL event scheduler runs `sp_sequence_loop` every 10 seconds (configurable via `sequencer.interval.seconds`).

- Dequeues unprocessed events and assigns them a monotonically-increasing `sequence` number per partition.
- Writes sequence assignments to the `sequences` table.
- Batch size is configurable via `boxy_config: sequencer.batch.size` (default 1000).
- Failures are logged to `background_job_errors` table.

### 3. Consumers

Registered consumers poll events via `ConsumerRepository.poll(consumerId, batchSize)`.

- Each consumer holds a cursor per topic/partition, tracking the last-read position.
- Polling acquires time-limited leases on cursors to avoid duplicate delivery across consumer instances.
- Lease duration is configurable via `boxy_config: lease.lock.seconds` (default 3 seconds).
- Consumers must call `CursorRepository.commit(consumerId, cursorPositions)` to durably advance their position.

### 4. Consumer GC (MySQL Event)

The MySQL event scheduler runs `sp_consumers__gc` every 10 seconds (configurable via `consumer_gc.interval.seconds`).

- Scans the `consumers` table for expired heartbeats (deadline < NOW).
- Deletes expired consumers and cleans up their leases.
- Prevents stale consumers from indefinitely blocking partitions.

---

## Startup and Shutdown Procedures

### Startup Checklist

1. **Prepare MySQL database**
   ```bash
   # 1. Verify MySQL 8.0+ is running and accepting connections
   mysql -h $DB_HOST -u $DB_USER -p$DB_PASSWORD -e "SELECT VERSION();"
   
   # 2. Ensure Liquibase changesets have been applied
   mvn clean install -DskipTests
   mvn liquibase:status
   # Should show all changesets in EXECUTED state.
   
   # 3. Verify the event scheduler is enabled
   mysql -h $DB_HOST -u $DB_USER -p$DB_PASSWORD events_db \
     -e "SHOW VARIABLES LIKE 'event_scheduler';"
   # Should return event_scheduler=ON
   
   # If OFF, enable it:
   mysql -h $DB_HOST -u $DB_USER -p$DB_PASSWORD events_db \
     -e "SET GLOBAL event_scheduler = ON;"
   ```

2. **Start the application**
   ```bash
   # Set environment variables
   export DB_HOST=mysql.example.com
   export DB_PORT=3306
   export DB_NAME=events_db
   export DB_USER=boxy_user
   export DB_PASSWORD=<password>
   export BOXY_LOG_LEVEL=INFO
   
   # Start the application (adjust jar and port as needed)
   java -Xmx4G -Xms2G \
     -jar boxy-app-1.0.0.jar \
     --server.port=8080
   ```

3. **Verify health**
   ```bash
   # Wait ~5 seconds for startup
   sleep 5
   
   # Call the health check endpoint
   curl -s http://localhost:8080/health | jq .
   
   # Should return:
   # {
   #   "status": "UP",
   #   "components": {
   #     "boxy": {
   #       "status": "UP",
   #       "details": {
   #         "connectivity": "OK",
   #         "sequencer": "OK - backlog 0",
   #         "consumer_gc": "OK - 0 expired consumers"
   #       }
   #     }
   #   }
   # }
   ```

### Shutdown Procedure

1. **Graceful shutdown (recommended)**
   ```bash
   # Send SIGTERM to the application (if running via systemd)
   systemctl stop boxy-app
   
   # Or via HTTP (if health endpoint supports shutdown)
   curl -X POST http://localhost:8080/actuator/shutdown
   
   # Wait for in-flight polls/commits to drain (~30 seconds)
   sleep 30
   
   # Verify all connections are closed
   mysql -h $DB_HOST -u $DB_USER -p$DB_PASSWORD \
     -e "SHOW PROCESSLIST;" | grep -i "boxy\|events_db"
   # Should return nothing
   ```

2. **Immediate shutdown (emergency)**
   ```bash
   # Kill the process
   kill -9 <pid>
   
   # Note: in-flight transactions will be rolled back by MySQL.
   # Consumers will have to re-acquire leases after restart.
   ```

---

## Scaling Consumers Horizontally

### Adding New Consumer Instances

1. **Deploy the new instance**
   ```bash
   # Start a new application instance with the same consumer_id or a different one
   # If using the same consumer_id, only one instance should run concurrently
   # (enforced by lease ownership).
   
   java -jar boxy-app-1.0.0.jar --consumer.id=consumer-group-1-instance-2
   ```

2. **Verify heartbeat registration**
   ```bash
   # Query the consumers table to confirm registration
   mysql -h $DB_HOST -u $DB_USER -p$DB_PASSWORD events_db \
     -e "SELECT id, subscription_id, heartbeat_deadline FROM consumers \
         WHERE id = 'consumer-group-1-instance-2' LIMIT 1;"
   # Should return a row with a recent heartbeat_deadline
   ```

3. **Monitor rebalancing**
   - Polling will automatically distribute leases across all active consumers.
   - It takes up to `lease.lock.seconds + 1` (default ~4s) for the load to rebalance.
   - Monitor `boxy.poll.latency` and `boxy.sequencer.lag` to confirm stability.

### Removing Consumer Instances

1. **Graceful shutdown**
   ```bash
   # Stop the instance normally (see Shutdown Procedure above)
   systemctl stop boxy-app-instance-2
   ```

2. **Verify cleanup**
   ```bash
   # Consumer should be garbage-collected after heartbeat expiry
   # (default 3 seconds + GC interval of 10 seconds)
   
   # Query GC status
   mysql -h $DB_HOST -u $DB_USER -p$DB_PASSWORD events_db \
     -e "SELECT job_name, MAX(error_time) FROM background_job_errors \
         WHERE job_name = 'consumer_gc' \
         GROUP BY job_name;"
   # Should show recent timestamps with no errors
   ```

3. **Monitor recovery**
   - Monitor `boxy.poll.latency` and throughput for the remaining instances.
   - They should automatically increase their polling probability to compensate.

### Scaling Best Practices

- **Consumer affinity**: Use a consistent consumer_id for each consumer instance so leases are transferable.
- **Gradual scaling**: Add/remove one instance at a time and wait for heartbeat stabilization (~30 seconds).
- **Monitor drain time**: Watch commit latency during scaling to ensure old leases are released promptly.
- **Batch size tuning**: Larger batches reduce per-event latency but increase GC pressure. Start with 100 and increase if `boxy.poll.latency` is stable.

---

## Monitoring Key Metrics

### Critical Metrics

| Metric | Type | Threshold | Action |
|--------|------|-----------|--------|
| `boxy.sequencer.lag` | Gauge | > 10,000 for > 60s | Sequencer may be stalled or deadlocked; see [Troubleshooting: Stalled Sequencer](#stalled-sequencer) |
| `boxy.consumer.gc.reaped` | Counter | Flat for > 120s | GC event may be stalled; check `background_job_errors` table |
| `hikaricp.connections.pending` | Gauge | > 0 for > 5s | Connection pool exhausted; increase pool size or reduce concurrency |
| `boxy.publish.latency` (p99) | Timer | > 50ms | Possible row lock contention or slow disk I/O |
| `boxy.poll.latency` (p99) | Timer | > 100ms | Large result sets or many partitions; consider reducing batch size |
| `boxy.commit.latency` (p99) | Timer | > 50ms | Possible cursor table lock contention |

### Secondary Metrics

- **`boxy.publish.latency` (p50)**: Should be < 5ms under normal load.
- **`boxy.poll.latency` (p50)**: Should be < 20ms under normal load.
- **`boxy.commit.latency` (p50)**: Should be < 10ms under normal load.
- **Connection pool active**: Should be < 80% of `maximumPoolSize` under sustained load.

### Logging Events

Enable detailed logging to diagnose issues:

```bash
# Set environment variable
export BOXY_SQL_LOG_LEVEL=DEBUG

# Or configure in logback.xml:
# <logger name="boxy.core.repository" level="DEBUG" />
```

This will log all SQL executions with parameter counts and error details.

---

## Troubleshooting Guide

### Stalled Sequencer

**Symptom**: `boxy.sequencer.lag` grows monotonically and never decreases.

**Diagnosis**:
```sql
-- Check if the sequencer event is running
SHOW EVENTS LIKE 'sequencer%' \G

-- Check for recent errors in background_job_errors
SELECT id, job_name, error_time, error_message 
FROM background_job_errors 
WHERE job_name = 'sequencer'
ORDER BY error_time DESC LIMIT 5;

-- Check if there are blocking locks on unprocessed_events
SHOW OPEN TABLES WHERE In_use > 0 AND name LIKE 'unprocessed%';

-- Check for deadlocked transactions
SHOW ENGINE INNODB STATUS \G
-- Look for LATEST DETECTED DEADLOCK section
```

**Resolution**:

1. **If event is disabled**:
   ```sql
   ALTER EVENT sequencer ENABLE;
   ```

2. **If event threw an error**:
   - Check the error message in `background_job_errors`.
   - Review the sequencer stored procedure logs for detailed errors.
   - Check if a unique constraint violation occurred (partition/sequence combination).

3. **If tables are locked**:
   ```sql
   -- Identify blocking session
   SELECT blocking_pid, blocked_pid, locked_resource 
   FROM sys.x$innodb_locks;
   
   -- Kill the blocking session (if safe)
   KILL <blocking_pid>;
   ```

4. **If deadlock detected**:
   - Review `sp_sequence` and `sp_sequence_loop` SQL for locking order violations.
   - Consider increasing `sequencer.batch.size` to reduce iteration frequency.

### Consumer Lease Issues

**Symptom**: Consumers fail to acquire leases; polls return empty result sets even though events are available.

**Diagnosis**:
```sql
-- Check lease expiry distribution
SELECT 
    l.consumer_id,
    COUNT(*) AS lease_count,
    MIN(l.locked_until) AS min_locked_until,
    MAX(l.locked_until) AS max_locked_until
FROM consumer_leases l
WHERE l.locked_until > NOW()
GROUP BY l.consumer_id;

-- Check for stale leases (locked_until in the past)
SELECT consumer_id, COUNT(*) AS stale_lease_count
FROM consumer_leases
WHERE locked_until < NOW() AND locked_until IS NOT NULL
GROUP BY consumer_id;
```

**Resolution**:

1. **If leases are held by a dead consumer**:
   ```sql
   -- Check if consumer is still alive
   SELECT id, heartbeat_deadline FROM consumers WHERE id = '<consumer_id>';
   
   -- If dead (heartbeat_deadline < NOW()), GC will clean it up in ~10 seconds
   -- Or manually delete:
   DELETE FROM consumer_leases 
   WHERE cursor_id IN (
       SELECT id FROM cursors WHERE subscription_id IN (
           SELECT id FROM subscriptions WHERE id = <sub_id>
       )
   ) AND consumer_id = '<dead_consumer_id>';
   ```

2. **If lease duration is too short**:
   ```sql
   -- Increase lease lock duration
   UPDATE boxy_config 
   SET config_value = '10' 
   WHERE config_key = 'lease.lock.seconds';
   ```

3. **If consumers are not being registered**:
   - Check application logs for `UNKNOWN_CONSUMER` errors.
   - Verify `ConsumerRepository.register()` is being called at startup.

### Slow Polls

**Symptom**: `boxy.poll.latency` p99 exceeds 100ms consistently.

**Diagnosis**:
```sql
-- Check partition distribution
SELECT p.partition_number, COUNT(s.id) AS sequence_count
FROM partitions p
LEFT JOIN sequences s ON s.partition_id = p.id
GROUP BY p.partition_number
ORDER BY sequence_count DESC;

-- Check for hot partitions (uneven distribution)
SELECT p.partition_number, 
       COUNT(DISTINCT c.id) AS cursor_count,
       MAX(s.sequence) - MIN(s.sequence) AS sequence_range
FROM partitions p
JOIN sequences s ON s.partition_id = p.id
LEFT JOIN cursors c ON c.partition_id = p.id
GROUP BY p.partition_number
HAVING sequence_range > 100000
ORDER BY sequence_range DESC;

-- Check if batch size is too large
SELECT 'Current batch size is set to:' AS msg;
SELECT config_value FROM boxy_config 
WHERE config_key = 'poll.batch.size' OR config_key = 'BOXY_POLL_BATCH_SIZE';
```

**Resolution**:

1. **If batch size is too large**:
   ```java
   // Reduce batch size per call
   consumerRepository.poll(consumerId, 50);  // instead of default 100
   ```

2. **If partitions are uneven**:
   - Re-examine the key distribution; key hashing should be uniform.
   - Consider adding more partitions if a single partition is the bottleneck.

3. **If hot partitions exist**:
   - Check `innodb_buffer_pool_hit_ratio` for partition pages in memory.
   - Increase `innodb_buffer_pool_size` if hit ratio is < 99%.

### Connection Pool Exhaustion

**Symptom**: `hikaricp.connections.pending` > 0; application threads wait for connections.

**Diagnosis**:
```sql
-- Check active connections from the application
SELECT id, user, time, info 
FROM INFORMATION_SCHEMA.PROCESSLIST 
WHERE user = '<boxy_user>';

-- Check which queries are holding connections
SELECT * FROM INFORMATION_SCHEMA.INNODB_TRX 
ORDER BY trx_started \G
```

**Resolution**:

1. **Identify long-running queries**:
   - Look for transactions in `INNODB_TRX` with `trx_started` > 10 seconds ago.
   - Kill them if they appear stuck: `KILL <thread_id>;`

2. **Increase pool size**:
   ```properties
   # In hikari.properties or environment:
   export BOXY_POOL_SIZE=60  # instead of default 20
   ```

3. **Reduce concurrency**:
   - Reduce the number of polling threads.
   - Implement request queuing/backpressure in the application.

### High Memory Usage (GC Pressure)

**Symptom**: Frequent GC pauses; `boxy.poll.latency` has occasional spikes.

**Diagnosis**:
```bash
# Enable GC logging
java -Xmx4G -Xms2G \
  -XX:+PrintGCDetails \
  -XX:+PrintGCDateStamps \
  -jar boxy-app-1.0.0.jar
```

**Resolution**:

1. **Reduce batch sizes**:
   - Smaller batches use less heap per poll.
   - Reduce `sequencer.batch.size` in `boxy_config`.

2. **Increase heap**:
   ```bash
   java -Xmx8G -Xms4G -jar boxy-app-1.0.0.jar
   ```

3. **Tune GC**:
   ```bash
   # Use ZGC for low-latency (Java 15+)
   java -XX:+UseZGC -Xmx4G -jar boxy-app-1.0.0.jar
   ```

---

## Maintenance Tasks

### Event Retention and Cleanup

See [docs/retention.md](retention.md) for a complete guide on event cleanup strategies.

**Quick reference**:
```sql
-- Daily cleanup of events older than 30 days
CALL sp_events__cleanup(
    DATE_SUB(NOW(), INTERVAL 30 DAY),  -- cutoff_time
    10000  -- batch_size
);
```

### Database Backup and Recovery

1. **Daily backup (using mysqldump)**:
   ```bash
   mysqldump -h $DB_HOST -u $DB_USER -p$DB_PASSWORD \
     --single-transaction \
     --lock-tables=false \
     events_db > backup-$(date +%Y%m%d).sql
   ```

2. **Binary log backups** (for point-in-time recovery):
   - Ensure `log_bin` and `binlog_format=ROW` are enabled in `my.cnf`.
   - Archive binary logs to a separate location:
     ```bash
     mysql -h $DB_HOST -u $DB_USER -p$DB_PASSWORD \
       -e "SHOW BINARY LOGS;" | tail -n +2 | awk '{print $1}' | while read log; do
       mysqlbinlog -h $DB_HOST -u $DB_USER -p$DB_PASSWORD $log > /backup/binlog/$log
     done
     ```

### Configuration Changes at Runtime

All Boxy configuration is stored in the `boxy_config` table and takes effect on the next operation (no restart required).

```sql
-- Update sequencer batch size
UPDATE boxy_config 
SET config_value = '2000' 
WHERE config_key = 'sequencer.batch.size';

-- Verify the change
SELECT config_key, config_value FROM boxy_config 
WHERE config_key = 'sequencer.batch.size';

-- Wait for the next sequencer event to apply the change
-- (event runs every 10 seconds by default)
WATCH -n 5 "
  SELECT COUNT(*) AS unprocessed_count FROM unprocessed_events;
"
```

### Monitoring Background Job Health

The `background_job_errors` table records all background job (sequencer and consumer GC) failures:

```sql
-- Recent errors (last hour)
SELECT id, job_name, error_time, error_message 
FROM background_job_errors 
WHERE error_time > DATE_SUB(NOW(), INTERVAL 1 HOUR)
ORDER BY error_time DESC;

-- Error summary
SELECT job_name, COUNT(*) AS error_count, MAX(error_time) AS latest_error
FROM background_job_errors 
WHERE error_time > DATE_SUB(NOW(), INTERVAL 24 HOUR)
GROUP BY job_name;
```

---

## Alerting Thresholds and Escalation

### Alert Rules (for Prometheus/Grafana)

| Alert Name | Condition | Severity | Action |
|------------|-----------|----------|--------|
| **SequencerBacklogGrowing** | `deriv(boxy_sequencer_lag[5m]) > 0 AND boxy_sequencer_lag > 10000` | Critical | Wake on-call; see [Stalled Sequencer](#stalled-sequencer) |
| **PublishLatencyHigh** | `histogram_quantile(0.99, rate(boxy_publish_latency_bucket[5m])) > 0.05` | Warning | Check disk I/O; consider increasing I/O threads |
| **PollLatencyHigh** | `histogram_quantile(0.99, rate(boxy_poll_latency_bucket[5m])) > 0.1` | Warning | Check partition distribution; reduce batch size |
| **CommitLatencyHigh** | `histogram_quantile(0.99, rate(boxy_commit_latency_bucket[5m])) > 0.05` | Warning | Check cursor table locks; scale horizontally |
| **PoolExhausted** | `hikaricp_connections_pending{pool="boxy-cp"} > 0` for 5m | Warning | Increase pool size or reduce concurrency |
| **DatabaseConnectivityLost** | `boxy_health_connectivity == 0` | Critical | Check MySQL network; restart database |

### Escalation Procedure

1. **On-call engineer** (0–15 min):
   - Acknowledge the alert.
   - Check the Grafana dashboard and recent logs.
   - Determine if immediate mitigation is needed.

2. **Mitigation** (15–30 min):
   - If sequencer is stalled: restart the event scheduler.
   - If pool is exhausted: increase pool size and trigger a rolling restart.
   - If backlog is growing: check for lock contention and analyze `SHOW ENGINE INNODB STATUS`.

3. **Root cause analysis** (30+ min):
   - Enable `BOXY_SQL_LOG_LEVEL=DEBUG` to trace the failing operation.
   - Review recent code deployments and configuration changes.
   - File a post-incident review if the alert was due to a systemic issue.

---

## Related Documentation

- [Configuration Tuning Guide](configuration.md) — Pool sizes, batch sizes, heartbeat intervals
- [Monitoring Guide](monitoring.md) — Metric definitions and Grafana dashboards
- [Event Retention and Cleanup](retention.md) — Retention strategy and cleanup procedures
- [Background Jobs Evaluation](background-jobs.md) — Sequencer and consumer GC design
