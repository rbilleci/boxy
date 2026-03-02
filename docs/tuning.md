# Configuration Tuning Guide

This guide provides detailed recommendations for tuning Boxy's performance across different workload profiles. It extends the [Configuration](configuration.md) doc with operational best practices.

## Quick Reference: Workload Profiles

Choose the profile that best matches your use case:

| Profile | Throughput Target | Latency Target | Example Workload |
|---------|-------------------|----------------|------------------|
| **Low-Latency** | < 10K events/sec | p99 < 10ms | Real-time dashboards, alerts |
| **High-Throughput** | 50K–100K events/sec | p99 < 50ms | Batch analytics, log streaming |
| **Cost-Optimized** | 1K–10K events/sec | p99 < 100ms | Compliance/audit logs |

---

## Connection Pool Sizing

The HikariCP connection pool is the most critical tuning parameter for Boxy throughput.

### Formula

```
Pool Size = (vCPU count × 2) + (effective_spindle_count)
```

For SSD/NVMe storage (effective_spindle_count = 1):

```
Pool Size = (vCPU count × 2) + 1
```

### Examples by Instance Type

| Instance | vCPU | RAM | Pool Size | Notes |
|----------|------|-----|-----------|-------|
| db.r6g.large | 2 | 16 GB | 5–8 | Dev/test environments |
| db.r6g.xlarge | 4 | 32 GB | 9–12 | Small production |
| db.r6g.2xlarge | 8 | 64 GB | 17–20 | Medium production |
| db.r6g.4xlarge | 16 | 128 GB | 33–36 | High-throughput production |

### Configuration

Set via environment variable or `hikari.properties`:

```bash
# Via environment (recommended for containers)
export BOXY_POOL_SIZE=20
export BOXY_POOL_MIN_IDLE=5

# Or via application config
hikari.maximumPoolSize=20
hikari.minimumIdle=5
```

### Validation

Check pool utilization via metrics:

```promql
# Alert if pool utilization exceeds 80%
hikaricp_connections_active / hikaricp_connections > 0.8
```

If alerts fire, increase pool size by 50% and monitor.

---

## Batch Size Tuning

Batch sizes affect latency per event and memory consumption.

### Poll Batch Size

The `poll` batch size determines how many events a consumer receives per call:

```java
// Default: 100 events per poll
consumerRepository.poll(consumerId, 100);
```

**Impact**:
- **Larger batches** (500–1000): Lower latency per event; higher memory/GC pressure
- **Smaller batches** (10–50): Higher per-event latency; lower GC pressure

**Tuning**:

```sql
-- Check current setting
SELECT config_value FROM boxy_config WHERE config_key = 'poll.batch.size';

-- Adjust at runtime (takes effect on next poll)
UPDATE boxy_config SET config_value = '250' WHERE config_key = 'poll.batch.size';
```

**Recommendation**:
- **Low-latency profile**: 50–100
- **High-throughput profile**: 500–1000
- **Cost-optimized profile**: 100–200

### Sequencer Batch Size

The sequencer processes unprocessed events in batches:

```sql
-- Check current setting
SELECT config_value FROM boxy_config WHERE config_key = 'sequencer.batch.size';

-- Adjust (default: 1000, max: 10000)
UPDATE boxy_config SET config_value = '2000' WHERE config_key = 'sequencer.batch.size';
```

**Impact**:
- **Larger batches**: Fewer event loop iterations; better throughput
- **Smaller batches**: Faster response time; more iterations

**Recommendation**:
- **Low-latency profile**: 500–1000
- **High-throughput profile**: 2000–5000
- **Cost-optimized profile**: 1000

### Publish Batch Size

The maximum batch size for multi-event publishes:

```sql
-- Check current setting
SELECT config_value FROM boxy_config WHERE config_key = 'max.batch.size';

-- Adjust (default: 1000, max: 100000)
UPDATE boxy_config SET config_value = '5000' WHERE config_key = 'max.batch.size';
```

**Recommendation**:
- **Low-latency profile**: 500
- **High-throughput profile**: 10000
- **Cost-optimized profile**: 1000

---

## Heartbeat and Lease Configuration

### Heartbeat Interval

The heartbeat interval determines how often consumers send keep-alive signals. Shorter intervals enable faster failure detection.

```sql
-- Check current setting (per subscription)
SELECT subscription_id, heartbeat_interval FROM subscription_topics;

-- Update (in seconds; default 3)
UPDATE boxy_config SET config_value = '5' WHERE config_key = 'heartbeat.interval.seconds';
```

**Impact**:
- **Shorter intervals** (1–2s): Faster failure detection; higher load on consumers table
- **Longer intervals** (5–10s): Lower load; slower detection of dead consumers

**Recommendation**:
- **Low-latency profile**: 2–3 seconds
- **High-throughput profile**: 5 seconds
- **Cost-optimized profile**: 10 seconds

### Lease Lock Duration

Time a consumer holds an exclusive lease on a cursor:

```sql
-- Check current setting
SELECT config_value FROM boxy_config WHERE config_key = 'lease.lock.seconds';

-- Adjust (default: 3)
UPDATE boxy_config SET config_value = '5' WHERE config_key = 'lease.lock.seconds';
```

**Impact**:
- **Shorter durations** (1–2s): Faster re-acquisition by other consumers; higher re-lock overhead
- **Longer durations** (5–10s): More stable lease ownership; slower failover

**Relationship to Heartbeat**:
- Lease duration should be >= heartbeat interval + 1 second
- Formula: `lease.lock.seconds >= heartbeat.interval.seconds + 1`

**Recommendation**:
- **Low-latency profile**: 3–4 seconds
- **High-throughput profile**: 5 seconds
- **Cost-optimized profile**: 10 seconds

---

## Interval Configuration

Background jobs (sequencer and consumer GC) run on intervals:

```sql
-- Sequencer interval (default: 10 seconds)
UPDATE boxy_config SET config_value = '5' WHERE config_key = 'sequencer.interval.seconds';

-- Consumer GC interval (default: 10 seconds)
UPDATE boxy_config SET config_value = '5' WHERE config_key = 'consumer_gc.interval.seconds';
```

**Tuning**:

| Interval | Low-Latency | High-Throughput | Cost-Optimized |
|----------|-------------|-----------------|----------------|
| Sequencer | 5s | 10s | 15s |
| Consumer GC | 5s | 10s | 20s |

**Lower intervals** = more responsive but higher CPU overhead.  
**Higher intervals** = lower CPU; slower backlog processing and GC.

---

## Payload and Data Size Limits

### Maximum Event Payload Size

```sql
-- Check current setting
SELECT config_value FROM boxy_config WHERE config_key = 'max.event.payload.bytes';

-- Adjust (default: 1048576 = 1 MiB; max: practical limit ~16 MiB)
UPDATE boxy_config SET config_value = '5242880' WHERE config_key = 'max.event.payload.bytes';
```

**Recommendation**:
- **General**: 1 MiB (1048576 bytes)
- **High-volume small events**: 256 KiB (262144 bytes)
- **Large event payloads**: 5–10 MiB (5242880–10485760 bytes)

> **Note**: Larger payloads increase memory per batch and row-lock duration.

### Maximum Batch Size

Covered above; limits the number of events in a single `publishBatch()` call.

---

## Complete Configuration Profiles

### Profile 1: Low-Latency (Real-time Dashboards)

**Target**: p99 latency < 10ms, throughput 5K–10K events/sec

```bash
# Pool
export BOXY_POOL_SIZE=12
export BOXY_POOL_MIN_IDLE=4

# Batch sizes
UPDATE boxy_config SET config_value = '100' WHERE config_key = 'poll.batch.size';
UPDATE boxy_config SET config_value = '500' WHERE config_key = 'sequencer.batch.size';
UPDATE boxy_config SET config_value = '500' WHERE config_key = 'max.batch.size';

# Intervals
UPDATE boxy_config SET config_value = '5' WHERE config_key = 'sequencer.interval.seconds';
UPDATE boxy_config SET config_value = '5' WHERE config_key = 'consumer_gc.interval.seconds';

# Heartbeat
UPDATE boxy_config SET config_value = '2' WHERE config_key = 'heartbeat.interval.seconds';
UPDATE boxy_config SET config_value = '3' WHERE config_key = 'lease.lock.seconds';

# Payload
UPDATE boxy_config SET config_value = '524288' WHERE config_key = 'max.event.payload.bytes';
```

### Profile 2: High-Throughput (Batch Analytics)

**Target**: p99 latency < 50ms, throughput 50K–100K events/sec

```bash
# Pool (scale with vCPU)
export BOXY_POOL_SIZE=33   # for 16 vCPU instance
export BOXY_POOL_MIN_IDLE=10

# Batch sizes (large for throughput)
UPDATE boxy_config SET config_value = '1000' WHERE config_key = 'poll.batch.size';
UPDATE boxy_config SET config_value = '5000' WHERE config_key = 'sequencer.batch.size';
UPDATE boxy_config SET config_value = '10000' WHERE config_key = 'max.batch.size';

# Intervals (longer for less overhead)
UPDATE boxy_config SET config_value = '10' WHERE config_key = 'sequencer.interval.seconds';
UPDATE boxy_config SET config_value = '10' WHERE config_key = 'consumer_gc.interval.seconds';

# Heartbeat
UPDATE boxy_config SET config_value = '5' WHERE config_key = 'heartbeat.interval.seconds';
UPDATE boxy_config SET config_value = '6' WHERE config_key = 'lease.lock.seconds';

# Payload (larger for bulk events)
UPDATE boxy_config SET config_value = '5242880' WHERE config_key = 'max.event.payload.bytes';
```

### Profile 3: Cost-Optimized (Compliance Logs)

**Target**: p99 latency < 100ms, throughput 1K–10K events/sec, minimal resource usage

```bash
# Pool (minimal)
export BOXY_POOL_SIZE=8
export BOXY_POOL_MIN_IDLE=2

# Batch sizes (trade latency for efficiency)
UPDATE boxy_config SET config_value = '200' WHERE config_key = 'poll.batch.size';
UPDATE boxy_config SET config_value = '1000' WHERE config_key = 'sequencer.batch.size';
UPDATE boxy_config SET config_value = '1000' WHERE config_key = 'max.batch.size';

# Intervals (longer; lower CPU)
UPDATE boxy_config SET config_value = '15' WHERE config_key = 'sequencer.interval.seconds';
UPDATE boxy_config SET config_value = '20' WHERE config_key = 'consumer_gc.interval.seconds';

# Heartbeat
UPDATE boxy_config SET config_value = '10' WHERE config_key = 'heartbeat.interval.seconds';
UPDATE boxy_config SET config_value = '12' WHERE config_key = 'lease.lock.seconds';

# Payload
UPDATE boxy_config SET config_value = '1048576' WHERE config_key = 'max.event.payload.bytes';
```

---

## MySQL Server Tuning

Extend the base configuration in [Configuration](configuration.md) with the following for specific workload profiles.

### Low-Latency Profile

```ini
# my.cnf
innodb_flush_log_at_trx_commit = 1      # Full ACID durability
innodb_buffer_pool_size = 16G            # Large buffer for hot pages
innodb_buffer_pool_instances = 4
innodb_log_file_size = 512M
innodb_read_io_threads = 4
innodb_write_io_threads = 4
innodb_page_cleaners = 4
```

### High-Throughput Profile

```ini
# my.cnf
innodb_flush_log_at_trx_commit = 2      # OS-buffered (faster)
innodb_buffer_pool_size = 32G            # Maximize working set
innodb_buffer_pool_instances = 8
innodb_log_file_size = 2G                # Fewer checkpoints
innodb_read_io_threads = 8
innodb_write_io_threads = 8
innodb_page_cleaners = 8
innodb_max_dirty_pages_pct = 50          # More dirty pages before flush
```

### Cost-Optimized Profile

```ini
# my.cnf
innodb_flush_log_at_trx_commit = 2      # OS-buffered
innodb_buffer_pool_size = 4G             # Smaller pool, acceptable miss rate
innodb_buffer_pool_instances = 2
innodb_log_file_size = 256M
innodb_read_io_threads = 2
innodb_write_io_threads = 2
innodb_page_cleaners = 2
```

---

## Performance Validation Checklist

After applying a tuning profile, verify the following metrics:

```sql
-- 1. Pool utilization
SELECT 
    pool,
    active_connections,
    idle_connections,
    ROUND(100.0 * active_connections / (active_connections + idle_connections), 2) AS utilization_pct
FROM <metrics_table>;

-- 2. Sequencer backlog
SELECT COUNT(*) AS unprocessed_events FROM unprocessed_events;

-- 3. Latency distribution
SELECT 
    'p50' AS percentile, 
    ROUND(AVG(CASE WHEN row_num <= total_rows * 0.5 THEN latency END), 2) AS ms
FROM <latency_metrics>
UNION ALL
SELECT 'p99', ROUND(AVG(CASE WHEN row_num <= total_rows * 0.99 THEN latency END), 2) FROM <latency_metrics>;

-- 4. Lock wait count
SHOW ENGINE INNODB STATUS \G
-- Look for: "1 row lock"
```

---

## Scaling Recommendations

### Horizontal Scaling (More Consumer Instances)

Add consumer instances when:
- Single instance cannot keep up with event throughput
- Poll latency increases due to batch size overload

```
per-instance throughput = 1 / (poll_latency_ms / 1000) * batch_size
Total throughput = per-instance * instance_count
```

Example:
- Poll latency: 20ms
- Batch size: 100 events
- Per-instance: (1 / 0.02) * 100 = 5000 events/sec
- With 10 instances: 50K events/sec

### Vertical Scaling (Larger Database Instance)

Upgrade the database when:
- Connection pool is consistently > 80% utilized
- Sequencer backlog is growing (throughput < ingest rate)

Example upgrade path:
```
db.r6g.2xlarge (20 pool) → db.r6g.4xlarge (33 pool) → db.r6g.12xlarge (97 pool)
```

---

## Related Documentation

- [Configuration](configuration.md) — Base configuration reference
- [Monitoring](monitoring.md) — Metrics to track during tuning
- [Benchmarks](benchmarks.md) — Measured performance baselines
- [Runbook](runbook.md) — Troubleshooting under load
