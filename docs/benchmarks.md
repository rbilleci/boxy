# Boxy — Benchmark Methodology & Baseline Numbers

> **Performance target:** 1 million events/second sustained throughput on production-grade hardware.

---

## Contents

1. [Methodology](#methodology)
2. [Test Classes](#test-classes)
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
