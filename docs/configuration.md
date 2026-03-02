# Boxy — Configuration Tuning Guide

This guide covers all performance-relevant settings for MySQL, Boxy stored procedures,
and the Java connection pool (HikariCP).  Values are given for three deployment sizes;
choose the column that matches your hardware tier.

> **Benchmark profiles** correspond to Maven profiles `bench-local`, `bench-cloud-small`,
> and `bench-cloud-prod`.  See [benchmarks.md](benchmarks.md) for hardware specs.

---

## MySQL Server Settings (`my.cnf`)

### Durability and I/O

| Variable | Bench-local | Cloud-small | Cloud-prod | Notes |
|---|---|---|---|---|
| `innodb_flush_log_at_trx_commit` | `0` | `1` | `2` | `0`=never fsync (test only); `1`=full ACID; `2`=OS-buffered (recommended for prod) |
| `sync_binlog` | `0` | `1` | `0` | `0`=no binlog fsync; `1`=sync per write; `0` is safe when GTID replication is not used |
| `innodb_doublewrite` | `0` | `1` | `1` | Disable only on tmpfs/RAM storage where crash recovery is not needed |
| `innodb_flush_method` | `nosync` | `O_DIRECT` | `O_DIRECT` | `O_DIRECT` bypasses OS buffer cache for InnoDB data files |

### Concurrency

| Variable | Recommended | Notes |
|---|---|---|
| `innodb_autoinc_lock_mode` | `2` | **Required.** Interleaved mode; no table-level AUTO_INCREMENT lock on multi-row INSERTs. Without this, `sp_events__publish_multi` serializes under concurrency. |
| `innodb_thread_concurrency` | `0` | Let InnoDB manage thread concurrency automatically. |
| `innodb_read_io_threads` | `4`–`8` | Increase for SSD/NVMe storage with many partitions. |
| `innodb_write_io_threads` | `4`–`8` | Increase for high publish throughput. |

### Buffer Pool

| Variable | Cloud-small | Cloud-prod | Notes |
|---|---|---|---|
| `innodb_buffer_pool_size` | `8G` | `32G`+ | Set to 70–80% of available RAM. Larger pool reduces I/O for hot partitions. |
| `innodb_buffer_pool_instances` | `4` | `8`+ | Reduce mutex contention; set to 1 per GB of buffer pool (capped at 64). |
| `innodb_log_file_size` | `512M` | `2G` | Larger redo log reduces checkpoint frequency under heavy write load. |

### Background Jobs

| Variable | Value | Notes |
|---|---|---|
| `event_scheduler` | `ON` | **Required.** Enables the MySQL event scheduler for `sp_sequence_loop` and `consumer_gc`. |
| `log_bin_trust_function_creators` | `1` | **Required.** Allows stored procedures/functions to be created without SUPER privilege. |

### Compression (Optional — Item #55)

| Variable | Value | Notes |
|---|---|---|
| `innodb_file_per_table` | `ON` | **Required for compression.** Each table has its own tablespace file. |

To enable InnoDB page compression for the `events` table (deferred until v0 baseline confirms I/O bottleneck):

```sql
ALTER TABLE events COMPRESSION='zstd' ROW_FORMAT=COMPRESSED;
OPTIMIZE TABLE events;  -- rewrites pages with compression applied
```

---

## Boxy Stored Procedure Settings

These are compile-time constants baked into the stored procedures.  To change them,
edit the relevant SQL file and deploy a new Liquibase changeset.

| Setting | Default | Procedure | Notes |
|---|---|---|---|
| Consumer lease duration | `3 s` | `sp_consumers__register` | `heartbeat_deadline = NOW() + INTERVAL 3 SECOND`. Increase for slow consumers; decrease for faster failover. |
| Poll batch size | `100` (default) | `sp_events__poll_v2` | Configurable per call: `sp_events__poll(consumer_id, batch_size)`. Clamp: [1, 10000]. |
| Sequencer min batch | `100` | `sp_sequence_loop_v2` | Starting batch size per sequencer iteration. |
| Sequencer max batch | `10000` | `sp_sequence_loop_v2` | Maximum batch size after dynamic doubling. |
| Sequencer idle sleep | `1 ms` | `sp_sequence_loop_v2` | Sleep between iterations when queue is empty. |
| Consumer GC batch size | `100` | `sp_consumers__gc_v2` | Max expired consumers removed per transaction batch. |

---

## HikariCP Connection Pool Settings

Boxy's Java layer uses `DataSourceProvider` (backed by HikariCP) to manage connections.
The following properties are relevant for production workloads.

### Recommended `hikari.properties` (or equivalent)

```properties
# Pool sizing: start with (core_count × 2) + effective_spindle_count
# For db.r6g.2xlarge (8 vCPU, NVMe): 8 × 2 + 1 = 17; round to 20
maximumPoolSize=20
minimumIdle=5

# Connection validation (use a lightweight ping query)
connectionTestQuery=SELECT 1
connectionTimeout=3000       # 3s: fail fast if pool is exhausted
idleTimeout=600000           # 10m: reclaim idle connections
maxLifetime=1800000          # 30m: rotate connections to avoid stale TCP

# MySQL-specific: keep connection open across polls
keepaliveTime=60000          # 1m: send keepalive to MySQL to prevent NAT timeout
```

### Pool Size Guidelines

| Deployment | Producers | Consumers | Pool size |
|---|---|---|---|
| Dev/local | 1–2 | 1–2 | 5–10 |
| Cloud-small (db.r6g.large) | 4–8 | 4–8 | 20–30 |
| Cloud-prod (db.r6g.2xlarge+) | 16–32 | 16–32 | 40–60 |

> **Rule of thumb:** pool size ≈ `(vCPU × 2) + 1`.  Above this, adding connections
> increases context-switching overhead faster than it increases throughput.

---

## Environment Variables

`DataSourceProvider` reads these environment variables at startup.

| Variable | Default | Description |
|---|---|---|
| `DB_HOST` | `localhost` | MySQL hostname or IP |
| `DB_PORT` | `3306` | MySQL port |
| `DB_NAME` | `events_db` | Database (schema) name |
| `DB_USER` | `user` | MySQL username |
| `DB_PASSWORD` | `password` | MySQL password |
| `DB_TYPE` | `mysql` | `mysql` or `postgres` |

---

## Production Checklist

Before deploying Boxy to production, verify the following:

- [ ] `innodb_autoinc_lock_mode=2` is set in `my.cnf`
- [ ] `event_scheduler=ON` is set in `my.cnf`
- [ ] `log_bin_trust_function_creators=1` is set in `my.cnf`
- [ ] `innodb_buffer_pool_size` is set to 70–80% of available RAM
- [ ] `innodb_flush_log_at_trx_commit` is set to `1` (full ACID) or `2` (OS-buffered)
- [ ] HikariCP `maximumPoolSize` is tuned for your vCPU count
- [ ] The `events_db` schema has been migrated with all Liquibase changesets
- [ ] `docs/benchmarks.md` v0 baseline numbers have been recorded on your hardware tier
- [ ] The sequencer event fires on schedule (verify: `SHOW EVENTS LIKE 'sequencer%'`)
- [ ] Consumer GC fires on schedule (verify: `SHOW EVENTS LIKE 'consumer_gc%'`)
