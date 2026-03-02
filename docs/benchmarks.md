# Boxy — Benchmark Methodology & Baseline Numbers

> **Performance target:** 1 million events/second sustained throughput on production-grade hardware.

---

## Contents

1. [Methodology](#methodology)
2. [Test Classes](#test-classes)
3. [Hardware Tiers & Maven Profiles](#hardware-tiers--maven-profiles)
4. [How to Run](#how-to-run)
5. [v0 Baseline Numbers](#v0-baseline-numbers)
6. [Publish Path Evaluation](#publish-path-evaluation)
7. [Interpreting Results](#interpreting-results)
8. [Tuning Reference](#tuning-reference)
3. [Hardware Tiers & Maven Profiles](#hardware-tiers--maven-profiles)
4. [How to Run](#how-to-run)
5. [v0 Baseline Numbers](#v0-baseline-numbers)
6. [Interpreting Results](#interpreting-results)
7. [Tuning Reference](#tuning-reference)

---

## Methodology

Every optimization change in Boxy follows a strict gate:

1. **Establish baseline** — run all benchmark scenarios on unmodified code and record numbers in this file.
2. **Implement change** — one logical change per commit.
3. **Re-run benchmarks** — same profile, same hardware tier.
4. **Gate decision** — merge only if throughput improves (or stays flat for correctness fixes). Regressions must be documented with justification.
5. **Update docs** — add a new row to the baseline tables below with the commit SHA and measured numbers.

All benchmarks use [HdrHistogram](https://github.com/HdrHistogram/HdrHistogram) for latency recording (nanosecond resolution, 3 significant digits). Percentiles are reported in milliseconds.

---

## Test Classes

### `BenchmarkIT` — Isolated Component Benchmarks

Measures each subsystem independently so bottlenecks can be identified in isolation.

| Test method | What it measures | Hot path |
|---|---|---|
| `publishAdvanced_singleThreaded` | Single-thread INSERT via partition ID | `sp_events__publish_advanced` |
| `publish_singleThreaded` | Single-thread INSERT via topic name | `sp_events__publish` |
| `publish_multiThreaded` | 10-thread concurrent INSERT | `sp_events__publish` × 10 |
| `sequencer_throughput` | MySQL event scheduler drain time | `sp_sequence` |
| `poll_throughput` | Poll call rate against pre-staged events | `sp_events__poll` |
| `commit_throughput` | Cursor commit call rate | `sp_cursors__commit` |

### `PipelineBenchmarkIT` — End-to-End Pipeline

Measures the complete event lifecycle: publish → sequence (MySQL scheduler) → poll → commit.

| Test method | Producers | Consumers | Events/producer |
|---|---|---|---|
| `pipeline_singleProducerSingleConsumer` | 1 | 1 | 2,000 |
| `pipeline_fourProducersFourConsumers` | 4 | 4 | 500 |
| `pipeline_eightProducersTwoConsumers` | 8 | 2 | 250 |

Metrics reported per scenario:

- **Publish throughput** (events/sec) and per-call p50 / p99 / p999 latency
- **Sequence time** (wall-clock seconds for sequencer to drain `unprocessed_events`)
- **Consume throughput** (events/sec polled + committed) and per-call p50 / p99 / p999 latency
- **End-to-end wall time** (first publish → last commit)
- **Sequencer lag** (`unprocessed_events` depth measured immediately after the publish phase)

---

## Hardware Tiers & Maven Profiles

| Profile | Target hardware | MySQL durability | Purpose |
|---|---|---|---|
| `bench-local` | Testcontainers + tmpfs | `flush_log_at_trx_commit=0`, `sync_binlog=0` | CI regression guard |
| `bench-cloud-small` | AWS db.r6g.large (2 vCPU / 16 GB), gp3 3000 IOPS | `flush_log_at_trx_commit=1` (full) | Small deployment baseline |
| `bench-cloud-prod` | AWS db.r6g.2xlarge+ (≥8 vCPU / ≥64 GB), io2 ≥64K IOPS | `flush_log_at_trx_commit=2` | 1M events/sec target |

> **Note:** `bench-local` numbers are *not* production-representative. The tmpfs storage and
> disabled durability settings inflate throughput by 5–20× compared to `bench-cloud-small`.
> Use `bench-local` only to detect regressions relative to a previous `bench-local` run on the
> same machine.

---

## How to Run

### Prerequisites

- JDK 25 (Temurin recommended)
- Docker (for `bench-local` / Testcontainers)
- Maven 3.9+

### bench-local (Testcontainers)

```bash
cd boxy-test
mvn test -Pbench-local
```

Output is written to stdout. Capture with `mvn test -Pbench-local | tee bench-local-$(git rev-parse --short HEAD).txt`.

### bench-cloud-small / bench-cloud-prod (external MySQL)

1. Provision a MySQL 8 instance matching the hardware tier spec above.
2. Initialize the schema: `boxy db init` (or `mvn liquibase:update -Dboxy.db.url=...`).
3. Export connection details:

```bash
export DB_HOST=<host>
export DB_PORT=3306
export DB_NAME=events_db
export DB_USER=boxy
export DB_PASSWORD=<secret>
```

4. Run the benchmark:

```bash
mvn test -Pbench-cloud-small   # or -Pbench-cloud-prod
```

### Recommended MySQL tuning for bench-cloud-prod

```ini
# /etc/mysql/conf.d/boxy-bench.cnf
[mysqld]
innodb_flush_log_at_trx_commit = 2
sync_binlog                    = 0
innodb_buffer_pool_size        = 48G        # 75% of RAM for db.r6g.2xlarge
innodb_io_capacity             = 10000
innodb_io_capacity_max         = 40000
innodb_log_file_size           = 2G
innodb_flush_method            = O_DIRECT
event_scheduler                = ON
log_bin_trust_function_creators = 1
```

---

## v0 Baseline Numbers

> **Status:** Pending first run. Numbers below are placeholders to be replaced after the first
> `bench-local` run on the reference development machine (Apple M4 Pro, 24 GB RAM).
> Record the commit SHA from `git rev-parse --short HEAD` when updating.

### bench-local (Testcontainers + tmpfs)

Recorded against commit: _TBD_
Machine: _TBD_

#### Isolated component benchmarks

| Benchmark | Throughput (events/sec) | p50 (ms) | p99 (ms) | p999 (ms) |
|---|---|---|---|---|
| `publishAdvanced_singleThreaded` | TBD | TBD | TBD | TBD |
| `publish_singleThreaded` | TBD | TBD | TBD | TBD |
| `publish_multiThreaded` (10 threads) | TBD | TBD | TBD | TBD |
| `sequencer_throughput` | TBD | — | — | — |
| `poll_throughput` | TBD | TBD | TBD | TBD |
| `commit_throughput` | TBD | TBD | TBD | TBD |

#### Pipeline benchmarks

| Scenario | Publish (events/sec) | Sequence time (s) | Consume (events/sec) | End-to-end (s) | Publish p99 (ms) | Poll p99 (ms) |
|---|---|---|---|---|---|---|
| 1P-1C (2000 events) | TBD | TBD | TBD | TBD | TBD | TBD |
| 4P-4C (2000 events) | TBD | TBD | TBD | TBD | TBD | TBD |
| 8P-2C (2000 events) | TBD | TBD | TBD | TBD | TBD | TBD |

### bench-cloud-small (db.r6g.large / gp3)

Recorded against commit: _TBD_
Machine: _AWS db.r6g.large, 2 vCPU / 16 GB, gp3 3000 IOPS_

_Numbers pending first cloud run._

### bench-cloud-prod (db.r6g.2xlarge+ / io2)

Recorded against commit: _TBD_
Machine: _AWS db.r6g.2xlarge, 8 vCPU / 64 GB, io2 64K IOPS_

_Numbers pending first cloud run._

---

---

## Publish Path Evaluation

This section documents the analysis behind publish-path optimizations (items #32–#36).

### Item #32 — Batch Publish Stored Procedure (sp_events__publish_multi v2)

**Background:** The v1 implementation loops N times, calling `sp_events__publish` once per event
(N JDBC round-trips, N transaction commits). At 1M events/sec this creates an unsustainable
commit rate.

**Change:** `sp_events__publish_multi` was rewritten to use `JSON_TABLE` to expand the input array
in one pass, resolve all partition IDs via a direct JOIN on `namespaces` + `topics` (bypassing the
MEMORY cache), and commit both `events` and `unprocessed_events` in a single transaction.

**Benchmark gate:** `BenchmarkIT.publishBatch_vs_singlePublish` compares the two paths.
Expected speedup ≥ 10× for batch sizes ≥ 10 events.

**Results:** _TBD — run `mvn test -Pbench-local` and update this table._

| Batch size | Single events/sec | Batch events/sec | Speedup |
|---|---|---|---|
| 50 | TBD | TBD | TBD |

### Item #33 — JDBC Batch Publish (EventRepository.publishBatch)

`EventRepository.publishBatch(List<PublishRequest>)` serialises the list to a JSON array and
issues a single `CALL sp_events__publish_multi(?)`. This reduces JDBC round-trips from N to 1
for a batch of N events. The serialisation is done in pure Java (no external JSON library
dependency) and escapes only the two characters that would break the JSON string format
(`\` and `"`).

### Item #34 — Topic Cache Lookup Overhead Evaluation

**Cache path:** `sp_events__publish` → `sp_topics__cache_get` → MD5 hash + MEMORY table lookup.
On cache miss, falls back to a JOIN on `namespaces` + `topics`.

**Bypass paths (already available):**
- `sp_events__publish_advanced(partition_id, data)` — caller supplies pre-resolved partition ID.
  Zero cache overhead. Used by producers that cache topic metadata on the application side.
- `sp_events__publish_multi` v2 — batch path always bypasses the cache via direct JOIN.

**Recommendation:** Measure cache lookup overhead as a percentage of total single-event publish
time using the `bench-cloud-small` profile. Only remove the cache from the single-event path if
overhead exceeds 10% of wall time. The MEMORY table lookup is a single index scan (O(1)) so
cache benefit is only visible when `topics` has a very large number of rows.

**Measurement (pending):** `publishAdvanced_singleThreaded` vs `publish_singleThreaded` latency
ratio. If p99 ratio > 1.1, the cache is adding measurable overhead; consider pre-resolving
partition IDs at the producer level using `sp_events__publish_advanced`.

### Item #35 — innodb_flush_log_at_trx_commit Durability vs Throughput

| Setting | Durability guarantee | Typical throughput (relative) | Risk |
|---|---|---|---|
| `1` (default) | Full — fsync on every COMMIT | 1× baseline | None |
| `2` | MySQL-crash safe; OS-crash may lose ≤ 1 s of commits | 2–5× | ≤ 1 second of data on OS crash |
| `0` | No per-commit fsync | 5–20× | ≤ 1 second of data on MySQL crash |

**Recommendation:** For event streaming workloads where downstream consumers are idempotent
and message redelivery is acceptable, `innodb_flush_log_at_trx_commit=2` with `sync_binlog=0`
is the standard production setting. This matches how Kafka and Pulsar configure their brokers.
Setting `=1` is appropriate for financial ledgers or systems where exactly-once delivery at the
persistence layer is required.

**Benchmark gate:** Run `publishBatch_vs_singlePublish` with `innodb_flush_log_at_trx_commit`
set to 0, 1, and 2 on `bench-cloud-small` hardware. Document the results in the table above.

### Item #36 — Batch-mode INSERT Evaluation

**Finding:** The `sp_events__publish_multi` v2 implementation (item #32) already uses a
single-statement multi-row `INSERT INTO events (data) SELECT ... FROM _pub_batch` which is
the batch-mode INSERT form. MySQL's `rewriteBatchedStatements=true` JDBC property (already
set in `DataSourceProvider`) applies only to JDBC `PreparedStatement.addBatch()` calls, not
to stored-procedure CALLs.

**Conclusion:** Item #36 is effectively superseded by item #32. The batch INSERT is now part
of the stored procedure itself, achieving the same goal without requiring `rewriteBatchedStatements`
to be active at the JDBC layer. No additional code changes required.

---

## Interpreting Results

**Sequencer lag** is the most important leading indicator of system health. If `unprocessed_events`
grows faster than the sequencer drains it, the system is in backpressure. The sequencer throughput
test (`sequencer_throughput`) establishes the drain ceiling; if publish throughput exceeds this
ceiling the queue grows unboundedly.

**p999 latency** is more important than p99 for consumer-visible responsiveness. A high p999 on
`poll_throughput` (e.g. > 10× p50) indicates lock contention in `sp_events__poll` under concurrent
consumers.

**End-to-end wall time** in `PipelineBenchmarkIT` includes sequencer wait time, which dominates for
small event counts. For large batches (>10K events), publish and consume time become significant.

---

## Tuning Reference

| MySQL variable | Bench-local | Cloud-small | Cloud-prod | Notes |
|---|---|---|---|---|
| `innodb_flush_log_at_trx_commit` | 0 | 1 | 2 | 0=no fsync; 1=full durability; 2=OS buffer flush |
| `sync_binlog` | 0 | 1 | 0 | 0=no fsync on binlog; 1=sync on every write |
| `innodb_doublewrite` | 0 | 1 | 1 | Skip double-write in local (tmpfs) only |
| `event_scheduler` | ON | ON | ON | Required for sequencer background job |
| `log_bin_trust_function_creators` | 1 | 1 | 1 | Required for stored procedure triggers |
| `performance_schema` | OFF | ON | ON | OFF in local for lower overhead |

See also: `BaseIT` MySQL container command-line flags for the `bench-local` configuration.
