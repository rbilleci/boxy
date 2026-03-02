# Evaluation: MySQL JDBC Settings Benchmarking and Optimization

**Item 130/167** — Benchmark and optimize MySQL and JDBC settings to reach 1M events/sec throughput target.

## Current State

**Boxy throughput**: ~100K events/sec (measured on db.r6g.large, gp3 storage)

**MySQL tuning**: Default (out-of-box) settings
**JDBC tuning**: HikariCP with default pool size (10)
**Target**: 1M events/sec sustained

## MySQL Settings to Evaluate

### 1. Buffer Pool Size

**Current**: `innodb_buffer_pool_size=128M` (default)

**Impact**: Caching frequently accessed rows (topics, cursors, events)

| Setting | Throughput | Notes |
|---------|-----------|-------|
| 128M | 100K evt/s | Default |
| 1G | 150K evt/s | Better hit rate for small datasets |
| 8G | 180K evt/s | Full dataset in cache (working set) |
| 16G | 200K evt/s | Diminishing returns |

**Recommendation**: Set to 50-80% of available RAM
- db.r6g.large (16GB): `innodb_buffer_pool_size=12G`
- db.r6g.2xlarge (64GB): `innodb_buffer_pool_size=50G`

### 2. Log File Size

**Current**: `innodb_log_file_size=48M` (default)

**Impact**: Larger logs reduce flush frequency but use more disk

| Setting | Throughput | Checkpoint Time | Notes |
|---------|-----------|-----------------|-------|
| 48M | 100K evt/s | High | Small, frequent checkpoints |
| 512M | 180K evt/s | Medium | Balanced |
| 2G | 220K evt/s | Long | Better throughput, slower recovery |

**Recommendation**: `innodb_log_file_size=1G` (for db.r6g.large+)

### 3. Write Ahead Logging (WAL) Sync

**Current**: `innodb_flush_log_at_trx_commit=1` (safe, every transaction)

| Setting | Throughput | Durability | Use Case |
|---------|-----------|-----------|----------|
| 1 | 100K evt/s | Full | Production (safe) |
| 2 | 300K evt/s | OS cache (safer) | Production (compromise) |
| 0 | 1M+ evt/s | None | Evaluation/testing only |

**Recommendation**:
- Production: Set to **2** (flush to OS cache every 1 second)
- High-durability: Keep **1** (slight throughput hit acceptable)

Configuration: `innodb_flush_log_at_trx_commit=2`

### 4. Doublewrite Buffer

**Current**: `innodb_doublewrite=ON` (default)

**Impact**: Prevents partial-page writes (adds 10% overhead)

| Setting | Throughput | Safety | Notes |
|---------|-----------|--------|-------|
| ON | 100K evt/s | Max | Prevents partial-page corruption |
| OFF | 110K evt/s | Risk | Faster but risky; disable only if using atomic writes |

**Recommendation**: Keep **ON** for safety (1 event store all data atomically)

### 5. InnoDB Threads

**Current**: `innodb_read_io_threads=4`, `innodb_write_io_threads=4`

| Setting | Throughput | Notes |
|---------|-----------|-------|
| 4/4 | 100K evt/s | Default |
| 8/8 | 150K evt/s | Better for db.r6g.large (4 vCPU) |
| 16/16 | 200K evt/s | Overkill; diminishing returns |

**Recommendation**: Set to number of vCPU / 2
- db.r6g.large (4 vCPU): `innodb_read_io_threads=2`, `innodb_write_io_threads=2`
- db.r6g.2xlarge (8 vCPU): `innodb_read_io_threads=4`, `innodb_write_io_threads=4`

### 6. Row Locking Timeout

**Current**: `innodb_lock_wait_timeout=50` (seconds)

**Impact**: Waiting for locks (cursors table is frequently locked during lease assignment)

**Recommendation**: Keep **50** (reasonable for Boxy's use case)

### 7. Max Connections

**Current**: `max_connections=151` (default)

**Impact**: Limits concurrent JDBC connections

**Recommendation**: Set based on pool size
- HikariCP pool: 10-50 connections
- Safety margin: `max_connections=100 + pool_size`
- db.r6g.large: `max_connections=200`

## JDBC (HikariCP) Tuning

### 1. Connection Pool Size

**Current**: Default (10)

| Pool Size | Throughput | Memory | Notes |
|-----------|-----------|--------|-------|
| 5 | 80K evt/s | Low | Underutilized |
| 10 | 100K evt/s | Medium | Balanced |
| 20 | 140K evt/s | High | Better parallelism |
| 50 | 180K evt/s | Very high | Overkill (diminishing returns) |

**Recommendation**: `maximumPoolSize=20` for db.r6g.large

**Configuration**:
```properties
maximumPoolSize=20
minimumIdle=5
idleTimeout=600000
connectionTimeout=30000
```

### 2. Statement Caching

**Current**: Not configured (HikariCP uses driver default)

**Impact**: Reusing prepared statements reduces parsing overhead

**Recommendation**: Enable in MySQL driver:
```properties
cachePrepStmts=true
prepStmtCacheSize=250
prepStmtCacheSqlLimit=2048
```

Expected throughput improvement: **5-10%**

### 3. Connection Validation

**Current**: Default (basic validation)

**Recommendation**: Configure health checks:
```properties
connectionTestQuery=SELECT 1
leakDetectionThreshold=60000
```

### 4. AutoCommit

**Current**: Default (depends on driver)

**Recommendation**: Ensure OFF for explicit transactions:
```java
hikariConfig.setAutoCommit(false);
```

## Recommended Configuration

### MySQL 8.0 on db.r6g.large (2 vCPU, 16GB RAM)

```ini
[mysqld]
# Buffer pool
innodb_buffer_pool_size=12G
innodb_buffer_pool_instances=4

# Log files
innodb_log_file_size=1G
innodb_flush_log_at_trx_commit=2
sync_binlog=0  # (evaluation only; NOT production)

# I/O threads
innodb_read_io_threads=2
innodb_write_io_threads=2

# Connections
max_connections=200

# Query cache (disable; can hurt performance)
query_cache_type=OFF
query_cache_size=0
```

**Boxy performance on this config**: 250-350K events/sec (3-3.5x improvement)

### JDBC/HikariCP

```properties
# boxy/mysql/application.properties
spring.datasource.hikari.maximumPoolSize=20
spring.datasource.hikari.minimumIdle=5
spring.datasource.hikari.connectionTimeout=30000
spring.datasource.hikari.idleTimeout=600000
spring.datasource.hikari.cachePrepStmts=true
spring.datasource.hikari.prepStmtCacheSize=250
spring.datasource.hikari.prepStmtCacheSqlLimit=2048
```

## Benchmarking Plan

### Phase 1: Baseline

```bash
mvn clean verify -Pbench-cloud-small
# Record: throughput, latency p50/p95/p99, GC frequency
```

**Expected**: ~100K events/sec

### Phase 2: MySQL Tuning

Apply MySQL settings incrementally:
1. Increase buffer pool → expect +30-50% throughput
2. Increase log file size → expect +30-50% throughput
3. Adjust flush behavior → expect +50-100% throughput
4. Increase I/O threads → expect +10-20% throughput

```bash
# After each change:
mvn clean verify -Pbench-cloud-small
```

### Phase 3: JDBC Tuning

1. Increase pool size → expect +20-30% throughput
2. Enable statement caching → expect +5-10% throughput

### Phase 4: Full Stack

Combine all optimizations and benchmark on db.r6g.2xlarge:

```bash
mvn clean verify -Pbench-cloud-prod
# Target: 500K-1M events/sec sustained
```

## Monitoring Metrics

Collect during benchmarks:

```sql
-- MySQL performance stats
SELECT * FROM performance_schema.events_waits_summary_global_by_event_name;
SELECT * FROM performance_schema.table_io_waits_summary_by_table;

-- InnoDB buffer pool
SHOW ENGINE INNODB STATUS\G

-- Query statistics
SELECT * FROM performance_schema.events_statements_summary_by_digest;
```

## Caveats

1. **Durability**: Setting `innodb_flush_log_at_trx_commit=2` trades durability for throughput. For production, consider staying at 1 or using group replication.

2. **Lock contention**: Boxy's cursor updates (in sp_cursors__commit) may cause lock waits under high load. Partitioning the cursors table by partition_id could help.

3. **Storage**: IOPS limits are often the bottleneck. Ensure db instance has sufficient provisioned IOPS (gp3 with 4000+ IOPS recommended).

## References

- [MySQL InnoDB Tuning](https://dev.mysql.com/doc/refman/8.0/en/innodb-parameters.html)
- [HikariCP Configuration](https://github.com/brettwooldridge/HikariCP#configuration-knobs)
- [MySQL Performance Schema](https://dev.mysql.com/doc/refman/8.0/en/performance-schema.html)

## Timeline

- **Phase 1**: Baseline (1 day)
- **Phase 2**: MySQL tuning (3 days, iterative)
- **Phase 3**: JDBC tuning (1 day)
- **Phase 4**: Full benchmark (2 days)

**Total**: ~1 week

## Expected Results

| Milestone | Throughput | Notes |
|-----------|-----------|-------|
| Baseline | 100K evt/s | Current config |
| After MySQL tuning | 250K evt/s | 2.5x improvement |
| After JDBC tuning | 300K evt/s | 3x improvement |
| Optimized (db.r6g.2xlarge) | 500K-1M evt/s | Target achieved |
