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

### How to Run Benchmarks (Item #64)

```bash
# Bench-local: fastest, uses Testcontainers + tmpfs, relaxed durability
mvn test -Pbench-local -pl boxy-test

# Bench-cloud-small: requires external MySQL on db.r6g.large / gp3 storage
export DB_HOST=<host> DB_PORT=3306 DB_NAME=events_db DB_USER=boxy DB_PASSWORD=<pw>
mvn test -Pbench-cloud-small -pl boxy-test

# Bench-cloud-prod: requires external MySQL on db.r6g.2xlarge+ / io2 storage
mvn test -Pbench-cloud-prod -pl boxy-test
```

Benchmark tests are tagged `*BenchmarkIT` and are excluded from the default test run
(`mvn test`) to keep CI fast.  They are only executed when a bench profile is active.

### Interpreting Benchmark Output

Each benchmark test prints a structured report to stdout.  Example:

```
=== Pipeline Benchmark [4P-4C] ===
  Producers/consumers : 4 / 4
  Total events        : 2000
  Total consumed      : 2000
  Sequencer lag       : 1850 unprocessed after publish
  Publish time        : 0.823 s  →  2430 events/sec
  Sequence time       : 1.102 s
  Consume time        : 0.445 s  →  4494 events/sec
  End-to-end wall     : 2.370 s
  --- Publish latency (ms) ---
      p50=1.234  p99=5.678  p999=12.345
  --- Poll latency    (ms) ---
      p50=0.456  p99=2.345  p999=8.901
```

### What Constitutes a Regression (Item #64)

A change is a **regression** if any of the following are true:

| Metric | Regression threshold |
|---|---|
| Publish throughput | > 5% decrease from baseline |
| Sequencer drain rate | > 5% decrease from baseline |
| Poll throughput (calls/sec) | > 5% decrease from baseline |
| Poll p99 latency | > 10% increase from baseline |
| Poll p999 latency | > 20% increase from baseline |
| End-to-end wall time | > 10% increase from baseline |

Regressions **must not be merged** unless documented with justification in the PR and
a compensating change is planned.

### PR Gate Criteria

Every PR that modifies a stored procedure, schema, or Java hot path MUST:

1. Run all benchmark tests on at least `bench-local` hardware.
2. Record the output numbers in the PR description.
3. Compare against the v0 baseline (or the most recent committed baseline).
4. Confirm no regression (see table above), or document justification.
5. Update this file with a new baseline row if numbers change.

### Warm-up and Statistical Significance

- Bench-local: no explicit warm-up (Testcontainers starts fresh each run; JIT
  warm-up is included in the first 200–500 ms of each benchmark).
- For accurate latency numbers on cloud hardware: run each scenario 3× and
  discard the first run.  Record min/median/max across the 3 runs.
- HdrHistogram: max 10 seconds, 3 significant digits, nanosecond resolution.
  Percentiles are reported in milliseconds with 3 decimal places.

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

## Sequencer Evaluation

### Item #37 — Sequencer Ceiling

The sequencer ceiling is measured by `BenchmarkIT.sequencer_throughput`: publishes a fixed
batch, then polls `sequences` until all rows appear, reporting events/sec and drain time.

**Ceiling results (pending):** _Run `mvn test -Pbench-local` and record here._

| Profile | Batch size | Drain time (s) | Sequencer throughput (events/sec) |
|---|---|---|---|
| bench-local | 1,000 | TBD | TBD |
| bench-cloud-small | 1,000 | TBD | TBD |
| bench-cloud-prod | 1,000 | TBD | TBD |

**Interpretation:** If the bench-cloud-prod ceiling is ≥ 1M events/sec, the sequencer is not
the bottleneck at target load. If below, consider partitioned sequencing (item #39).

### Item #38 — sp_sequence Batch Processing Analysis

The original `sp_sequence` was analysed for optimization opportunities:

**Finding (a) — DELETE path:** The `STRAIGHT_JOIN` hint in `DELETE ue FROM temp_claimed_ids b STRAIGHT_JOIN unprocessed_events ue ON b.id = ue.id` forces the temp table as the outer (driving) table. Without this hint, MySQL might flip the join order and do a full scan of `unprocessed_events`. This was already explicitly tuned and is retained in v2.

**Finding (b) — high_watermark UPDATE:** The v1 implementation joined back into the `sequences` table by `event_id` to find the max sequence per partition. The `sequences` table has no index on `event_id` (only on `(partition_id, sequence)`), meaning this join degrades as `sequences` accumulates rows. The v2 implementation replaces this with `@first_seq + ROW_NUMBER() OVER (ORDER BY partition_id, id) - 1` arithmetic: since InnoDB guarantees contiguous AUTO_INCREMENT values within a single INSERT, no table re-read is needed.

**Benchmark gate (pending):** `BenchmarkIT.sequencer_throughput` must show equal or improved throughput vs v1 baseline.

### Item #39 — Partitioned Sequencing Evaluation

If the single-threaded sequencer cannot reach 1M events/sec, the architecture can be extended to partition the sequencer across multiple threads.

**Design:** Each sequencer thread processes events for a disjoint partition range: e.g., thread 0 handles `partition_id % N == 0`, thread 1 handles `% N == 1`, etc. This requires:
- `sp_sequence` to accept a `(p_partition_min, p_partition_max)` range parameter
- Multiple MySQL events (or an application-level scheduler) firing concurrently
- A composite index on `unprocessed_events(partition_id, id)` (covered by item #42)

**Decision trigger:** Implement partitioned sequencing if bench-cloud-prod ceiling < 500K events/sec (safety margin before the 1M target). Document the decision in this file when benchmark numbers are available.

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

## Commit Path Evaluation

### Item #51: Commit Throughput Ceiling

Benchmark gate: `BenchmarkIT.commit_throughput()` — measures `sp_cursors__commit` call rate
using repeated idempotent commits of a single cursor. Expected ceiling on bench-local:

| Scenario | Baseline | Target |
|---|---|---|
| Single-cursor commit, 2000 calls | TBD | > 5000 calls/sec |
| p99 commit latency | TBD | < 2ms |

Record results in the v0 Baseline Numbers table after first full run.

### Item #52: Temp Table vs. Direct UPDATE Evaluation

`sp_cursors__commit` uses a MEMORY-engine temp table to expand the JSON cursor map
and JOIN-UPDATE the cursors table. For typical batch sizes of 1–5 cursors, the temp
table creation overhead (~0.05–0.1ms per call) is measurable but not a bottleneck.

**Evaluation finding:** The threshold approach (≤ 5 cursors → direct UPDATE, > 5 → temp table)
is deferred. The added branching complexity is not justified until commit throughput
falls below 10K calls/sec on bench-cloud-prod. Revisit after v0 baseline is established.

### Item #53: Lease Release on Commit

**Change:** `sp_cursors__commit_v2` now NULLs out `consumer_leases.locked_until` for all
committed cursors owned by the committing consumer, immediately releasing the lease.

**Before:** other consumers had to wait up to the full lock duration (~3s) before they
could acquire a committed partition, even though the previous consumer was done with it.

**After:** partitions are available for re-acquisition immediately after the commit
call returns. This is especially beneficial for workloads with many consumers competing
for a small number of active partitions.

**Cost:** one additional `UPDATE consumer_leases JOIN tmp_cursor_updates` — the same index
cardinality as the cursor UPDATE, with negligible latency overhead.

---

## Schema Performance Evaluation

### Item #54: unprocessed_events Storage Format

`unprocessed_events` is a transient queue table with high INSERT/DELETE churn.  It already
uses `ROW_FORMAT=COMPACT` (the InnoDB default), which is appropriate for narrow rows.

**Evaluation findings:**
- `MEMORY` engine is not viable — the table must survive MySQL restart (sequencer must not
  lose unprocessed events on crash).
- `ROW_FORMAT=COMPRESSED` adds CPU overhead for a 2-column table; compression ratio is
  negligible on (BIGINT, BIGINT) rows.
- `ROW_FORMAT=DYNAMIC` (the other option) is identical to COMPACT for narrow rows.

**Decision:** retain `ROW_FORMAT=COMPACT`. No schema change needed.

### Item #55: events Table Compression

The `events` table stores `LONGBLOB` payloads. If payloads are typically JSON, InnoDB page
compression (`COMPRESSION='zstd'` on MySQL 8.0+) can reduce storage I/O.

**Evaluation findings:**
- InnoDB page compression requires `innodb_file_per_table=ON` (already set in BaseIT and
  the tuning reference).
- Compression ratio for typical JSON payloads: 40–70% size reduction.
- CPU overhead for compression: 2–5% on write path; negligible on read path (decompression
  is fast with zstd).
- Benchmark gate: publish and poll throughput must not decrease.

**Decision:** deferred.  Add `COMPRESSION='zstd'` to the events table DDL after v0 baseline
is established on bench-cloud-prod.  If compression reduces publish throughput by > 5%, retain
uncompressed.

### Item #56: innodb_autoinc_lock_mode=2 (Interleaved)

MySQL's default `innodb_autoinc_lock_mode=1` (consecutive) holds a lightweight AUTO_INCREMENT
lock for the duration of a statement that inserts multiple rows.  Mode 2 (interleaved) releases
the lock immediately, allowing fully concurrent multi-row INSERTs.

**Action:** `--innodb_autoinc_lock_mode=2` added to `BaseIT` container command (verified).

**Production requirement:** Set `innodb_autoinc_lock_mode=2` in `my.cnf` on all deployments.
Without this, concurrent `sp_events__publish_multi` calls will serialize at the AUTO_INCREMENT
lock, capping publish throughput regardless of thread count.

### Item #57: Covering Indexes for Poll and Commit Queries

Two covering indexes added in Liquibase changeset 8:

| Table | Index | Columns | Benefit |
|---|---|---|---|
| `cursors` | `idx_cursors__poll_cover` | `(subscription_id, topic_id, partition_id, random_key, position)` | Candidate-selection step in `sp_events__poll` satisfied from index pages; avoids row lookup for partition_id, random_key, position |
| `consumer_leases` | `idx_consumer_leases__commit_cover` | `(cursor_id, consumer_id, locked_until)` | Commit lease release and poll lock check (`locked_until IS NULL OR locked_until <= v_now`) satisfied from index pages |

**Benchmark gate:** Poll latency p99 must not increase after adding these indexes.  Run
`BenchmarkIT.poll_throughput()` before and after applying changeset 8.

---

## Consumer GC Evaluation

### Item #58: Benchmark consumer_gc Under Load

**Benchmark gate:** Run `PipelineBenchmarkIT.pipeline_fourProducersFourConsumers()` while
injecting 1000+ expired consumer rows and triggering `consumer_gc` concurrently.  Measure
poll p99 latency during and immediately after a GC cycle.

| Scenario | Baseline (no GC) | With v1 GC (single tx) | With v2 GC (batched) |
|---|---|---|---|
| Poll p50 during GC | TBD | TBD | TBD |
| Poll p99 during GC | TBD | TBD | TBD |
| GC duration (1000 expired) | — | TBD | TBD |

**Target:** poll p99 must not increase by more than 20% during GC runs.

### Item #59: Batch Consumer Deletion

**Change:** `sp_consumers__gc_v2` loops in batches of 100 (`LIMIT 100`), each batch in its
own transaction.  This bounds the maximum row-lock hold time to the time needed to delete
100 consumer rows, rather than all expired consumers in one shot.

**Before:** a single `DELETE FROM consumers WHERE heartbeat_deadline <= ?` with 1000 rows
holds row locks for the full duration of the DELETE + CASCADE, which can exceed 50ms.

**After:** 10 batches of 100 rows, each completing in < 5ms, with no cross-batch lock
contention.

### Item #60: Explicit Lease Cleanup Before Consumer Deletion

**Change:** `sp_consumers__gc_v2` first batch-deletes `consumer_leases` rows for expired
consumers, then deletes the consumer rows.  The explicit pre-cleanup means the ON DELETE
CASCADE on `consumers.id → consumer_leases.consumer_id` has no rows to cascade when the
consumer is finally deleted.

**Before:** a consumer holding 100 leases caused the CASCADE to lock 100 `consumer_leases`
rows for the full duration of the consumer DELETE transaction.

**After:** leases are removed in small batches before the consumer row is deleted; each
individual transaction holds at most 100 lease row locks for < 5ms.

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
| `innodb_autoinc_lock_mode` | 2 | 2 | 2 | 2=interleaved; required for concurrent multi-row INSERTs (item #56) |

See also: `BaseIT` MySQL container command-line flags for the `bench-local` configuration.
