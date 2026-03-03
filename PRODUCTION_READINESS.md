# Boxy — Production Readiness Plan

> A comprehensive plan covering foundational work, infrastructure/quality improvements, and feature work tracked in GitHub Issues. Items are grouped by category, tagged with severity, and cross-referenced with GitHub Issue numbers where applicable.
>
> **Severity key:** 🔴 CRITICAL · 🟠 HIGH · 🟡 MEDIUM · 🟢 LOW
>
> **Totals:** 156 items — 18 Critical, 50 High, 55 Medium, 33 Low
>
> **Performance target:** 1M events/second sustained throughput on production-grade hardware

---

## Phase 0: Foundational Work (Do First)

These items establish the project foundation that all subsequent work depends on. They should be tackled before anything else because they reshape the project structure, tooling, and development workflow that everything else builds on.

### 0A. Agent & AI-Assisted Development Optimization

Boxy is developed heavily with AI agents (Codex, Junie, Claude). Optimizing for this workflow first means every subsequent task benefits from better agent context.

| # | Sev | Item | Issue |
|---|-----|------|-------|
| 1 | 🔴 | Create `CLAUDE.md` at project root with project overview, architecture summary, module map, build commands, test commands, coding conventions, and key design decisions so agents can orient immediately | [#56](https://github.com/rbilleci/boxy/issues/56) |
| 2 | 🔴 | Create `AGENTS.md` documenting agent workflows: branch naming conventions (`codex/*`, `junie/*`), PR expectations, which files agents should/shouldn't modify, and stored procedure conventions | [#56](https://github.com/rbilleci/boxy/issues/56) |
| 3 | 🟠 | Create `CONTRIBUTING.md` with human and agent contributor guidelines, code style, commit message format, and PR review process | [#56](https://github.com/rbilleci/boxy/issues/56) |
| 4 | 🟠 | Add MCP server configuration for Boxy development (database context, schema introspection, test execution) | [#56](https://github.com/rbilleci/boxy/issues/56) |
| 5 | 🟡 | Add `.cursorrules` / `.claude` project intelligence files with Boxy-specific patterns and anti-patterns | [#56](https://github.com/rbilleci/boxy/issues/56) |

### 0B. JDK 25 Upgrade

Upgrading the JDK early avoids rework — every new module, class, and test written after this point will target the correct platform. JDK 25 also unlocks features relevant to the CLI (virtual threads for Loom-based async, improved GraalVM native-image support).

| # | Sev | Item | Issue |
|---|-----|------|-------|
| 6 | 🔴 | Update parent POM `maven.compiler.source` and `maven.compiler.target` from 21 to 25 | — |
| 7 | 🔴 | Update CI and development environment documentation to require JDK 25 | — |
| 8 | 🟠 | Audit all dependencies for JDK 25 compatibility (HikariCP 6.3, Liquibase 4.32, MySQL Connector 8.4, Testcontainers 1.21) | — |
| 9 | 🟠 | Adopt JDK 25 features where beneficial: structured concurrency for worker APIs, improved pattern matching in repositories, virtual threads for connection handling | — |
| 10 | 🟡 | Update Maven Surefire plugin and compiler plugin versions for JDK 25 support | — |

### 0C. Module Restructuring

The current two-module layout (`boxy-core`, `boxy-db`) conflates concerns. Restructuring now means new code goes directly into the right module.

| # | Sev | Item | Issue |
|---|-----|------|-------|
| 11 | 🔴 | Restructure into proper Maven modules: `boxy-db` (Liquibase migrations and SQL), `boxy-core` (domain records, repositories, `DataSourceProvider`, MySQL/PostgreSQL implementations), `boxy-cli` (native CLI application), `boxy-test` (shared test infrastructure, `BaseIT`, `TestData`) | [#38](https://github.com/rbilleci/boxy/issues/38) |
| 12 | 🟠 | Ensure `boxy-core` supports both MySQL and PostgreSQL through unified repository implementations | — |
| 13 | 🟠 | Move integration test infrastructure (`BaseIT`, `TestData`) into `boxy-test` shared test module | — |
| 14 | 🟡 | Establish consistent package naming convention: `boxy.core.*` for all implementations, with database-specific SQL in `boxy-db` | — |

### 0D. CLI Implementation

The CLI is a primary user-facing tool that also serves as the integration test bed for the module restructuring. Building it early validates the module boundaries.

| # | Sev | Item | Issue |
|---|-----|------|-------|
| 16 | 🔴 | Create `boxy-cli` Maven module with GraalVM native-image build (initially targeting macOS ARM/aarch64) | [#37](https://github.com/rbilleci/boxy/issues/37) |
| 17 | 🔴 | Implement `boxy db init` command — initialize a new Boxy schema via Liquibase | [#37](https://github.com/rbilleci/boxy/issues/37) |
| 18 | 🔴 | Implement `boxy db migrate` command — run pending Liquibase migrations | [#37](https://github.com/rbilleci/boxy/issues/37) |
| 19 | 🟠 | Implement `boxy event publish <path> <topic> --key <key> --data <data>` command | [#37](https://github.com/rbilleci/boxy/issues/37) |
| 20 | 🟠 | Implement `boxy event listen <subscription> <topics...>` command — register, poll, and print events to stdout (Ctrl+C deregisters gracefully) | [#37](https://github.com/rbilleci/boxy/issues/37) |
| 21 | 🟠 | Implement `boxy namespace create/delete/rename/move` management commands | [#37](https://github.com/rbilleci/boxy/issues/37) |
| 22 | 🟠 | Implement `boxy topic create/delete` management commands | [#37](https://github.com/rbilleci/boxy/issues/37) |
| 23 | 🟠 | Implement `boxy subscription create/delete/subscribe/unsubscribe` management commands | [#37](https://github.com/rbilleci/boxy/issues/37) |
| 24 | 🟠 | Structure CLI build with Maven profiles for cross-platform native-image: `native-macos-arm64` (default), `native-macos-x64`, `native-linux-arm64`, `native-linux-x64`, `native-windows-x64` | [#37](https://github.com/rbilleci/boxy/issues/37) |
| 25 | 🟡 | Add GraalVM reflection/resource configuration for Liquibase, HikariCP, and MySQL driver native-image compatibility | [#37](https://github.com/rbilleci/boxy/issues/37) |
| 26 | 🟡 | Use picocli or similar for CLI argument parsing (GraalVM-friendly, generates man pages) | [#37](https://github.com/rbilleci/boxy/issues/37) |
| 27 | 🟢 | Add `boxy db status` command — show current schema version and pending migrations | [#37](https://github.com/rbilleci/boxy/issues/37) |

---

## Phase 1: Polling System Review & Performance Optimization

> **Goal:** Drive sustained throughput to 1M events/second on production-grade hardware (e.g. AWS r6g.2xlarge or equivalent with provisioned IOPS MySQL). Every change in this phase follows a strict discipline: establish a benchmark baseline _before_ the change, implement the change, re-run the benchmark, and only merge if the result is a net improvement (or is a correctness fix, in which case regression is acceptable). All changes must update project documentation (README, stored procedure comments, Javadoc, and the configuration tuning guide).

### 1A. Benchmark Infrastructure (Do First)

Before optimizing anything, we need repeatable, production-representative benchmarks. The existing `BenchmarkIT` measures single-threaded and multi-threaded publish latency, but doesn't cover the full pipeline (publish → sequence → poll → commit) or run at production scale.

| # | Sev | Item | Details |
|---|-----|------|---------|
| 28 | 🔴 | **Build end-to-end throughput benchmark** | Create a new `PipelineBenchmarkIT` that measures the complete path: N producer threads publishing events → sequencer processing → M consumer threads polling and committing. Report: events/sec ingested, events/sec consumed, p50/p99/p999 end-to-end latency (publish timestamp to consumer receipt), sequencer lag (unprocessed_events depth). Run on Testcontainers locally for CI, and on dedicated hardware for official numbers. **Update:** Add benchmark methodology section to README. |
| 29 | 🔴 | **Build isolated component benchmarks** | Separate benchmarks for each hot path: (a) publish throughput (events/sec into `events` + `unprocessed_events`), (b) sequencer throughput (events/sec through `sp_sequence`), (c) poll throughput (events/sec returned per consumer), (d) commit throughput (cursor updates/sec). Each benchmark should output HdrHistogram percentile distributions. **Update:** Document baseline numbers in a new `docs/benchmarks.md`. |
| 30 | 🟠 | **Add benchmark profiles for different hardware tiers** | Maven profiles for: `bench-local` (Testcontainers, tmpfs, relaxed durability — for CI regression checks), `bench-cloud-small` (db.r6g.large, gp3 storage — typical small deployment), `bench-cloud-prod` (db.r6g.2xlarge+, io2 storage — target 1M events/sec). Document hardware specs and MySQL tuning parameters for each profile. **Update:** Add hardware requirements section to operational runbook. |
| 31 | 🟠 | **Establish baseline numbers before any optimization** | Run all benchmarks on current code across all profiles. Record and commit results to `docs/benchmarks.md` as the "v0 baseline." All subsequent changes reference improvements relative to this baseline. **Update:** `docs/benchmarks.md` with baseline data. |

### 1B. Publish Path Optimization

The publish path is currently: resolve topic (cache lookup) → CRC32 partition → INSERT event → INSERT unprocessed_event. Each publish is a separate transaction with a separate stored procedure call. At 1M events/sec, this means 1M round-trips and 1M transaction commits per second.

| # | Sev | Item | Details |
|---|-----|------|---------|
| 32 | 🔴 | **Implement true batch publish stored procedure** | `sp_events__publish_multi` currently loops and calls `sp_events__publish` once per event (N procedure calls, N transactions). Replace with a single-transaction bulk INSERT that processes the entire JSON array in one pass: one `INSERT INTO events ... SELECT FROM JSON_TABLE`, one `INSERT INTO unprocessed_events`, one COMMIT. This alone could improve publish throughput 10–50x for batch publishes. **Benchmark gate:** Batch publish throughput must exceed N × single-publish throughput. **Update:** Stored procedure inline docs, README publish section, `docs/benchmarks.md`. |
| 33 | 🟠 | **Add JDBC batch publish in EventRepository** | Add a `publishBatch(List<Event>)` method that sends a single `CALL sp_events__publish_multi(?)` with a JSON array, instead of N individual calls. This reduces JDBC round-trips from N to 1. **Benchmark gate:** Java-side batch publish must show reduced latency vs. N individual publishes. **Update:** Javadoc on EventRepository, quickstart examples. |
| 34 | 🟠 | **Evaluate removing the topic cache lookup from the publish hot path** | `sp_events__publish` calls `sp_topics__cache_get` on every publish, which does an MD5 hash + MEMORY table lookup + potential cache-miss fallback. For the batch path, this means N cache lookups per batch. Consider: (a) resolving topic_id once at the client SDK level and using `sp_events__publish_advanced` (partition_id, data) for hot-path publishes, or (b) adding a batch-aware cache that resolves all distinct topics in one pass. **Benchmark gate:** Measure cache lookup overhead as % of total publish time; only change if >10%. **Update:** README architecture decisions, client protocol docs. |
| 35 | 🟡 | **Evaluate `innodb_flush_log_at_trx_commit=2` for publish throughput** | Document the durability vs. throughput tradeoff. With `=1` (full durability), each commit requires an fsync. With `=2`, the log is flushed to OS buffer but not fsynced per commit — up to 1 second of data could be lost on OS crash but not on MySQL crash. This is a common production setting for event systems. **Benchmark gate:** Measure publish throughput at settings 0, 1, and 2; document the tradeoff. **Update:** Configuration tuning guide, operational runbook. |
| 36 | 🟡 | **Evaluate batch-mode INSERT for events table** | Test whether `INSERT INTO events (data) VALUES (row1), (row2), ...` (multi-value INSERT) outperforms individual inserts within the stored procedure, given that MySQL's `rewriteBatchedStatements=true` is already enabled on the JDBC side. **Benchmark gate:** Must show measurable throughput improvement. **Update:** `docs/benchmarks.md`. |

### 1C. Sequencer Optimization

The sequencer is the single-threaded bottleneck that converts unprocessed events into sequenced, partition-ordered events. At 1M events/sec ingest, the sequencer must process 1M events/sec or the `unprocessed_events` table will grow unboundedly.

| # | Sev | Item | Details |
|---|-----|------|---------|
| 37 | 🔴 | **Benchmark the sequencer ceiling** | Determine the maximum events/sec the current sequencer can process on target hardware. This establishes whether the sequencer is actually the bottleneck. If it can already handle 1M+, focus optimization elsewhere. **Update:** `docs/benchmarks.md` with sequencer ceiling. |
| 38 | 🟠 | **Optimize `sp_sequence` batch processing** | Profile the sequencer transaction: INSERT INTO sequences, DELETE FROM unprocessed_events, UPDATE partitions. The STRAIGHT_JOIN hint on the DELETE suggests this was already tuned. Investigate: (a) whether the DELETE can use a multi-table DELETE with the temp table more efficiently, (b) whether the high_watermark UPDATE can be folded into the INSERT using a trigger or computed from the AUTO_INCREMENT value directly. **Benchmark gate:** Sequencer throughput must not decrease. **Update:** Stored procedure comments, `docs/benchmarks.md`. |
| 39 | 🟠 | **Evaluate partitioned sequencing** | If the single sequencer can't reach 1M events/sec, evaluate running multiple sequencer threads, each responsible for a subset of partitions. This would require changing `sp_sequence` to accept a partition range, and running multiple MySQL events or application-level schedulers. **Benchmark gate:** Total sequencing throughput across all threads must exceed single-thread baseline. **Update:** Architecture decisions docs, README. |
| 40 | 🟡 | **Tune sequencer batch size dynamically** | Currently hardcoded to 1000. If the unprocessed_events table has 50,000 rows, processing 1000 at a time means 50 iterations. Dynamically adjust batch size based on queue depth (e.g. min(queue_depth, 10000)) to reduce iteration overhead during bursts. **Benchmark gate:** Burst recovery time (time to drain a backlog of N events) must improve. **Update:** Configuration tuning guide. |
| 41 | 🟡 | **Reduce sequencer idle sleep from 10ms to 1ms** | The current `SLEEP(0.01)` in the sequencer loop adds 10ms of latency per idle iteration. Reducing to 1ms (`SLEEP(0.001)`) would improve cold-partition latency from ~10ms to ~1ms, at the cost of more CPU cycles when idle. **Benchmark gate:** Cold-partition p99 latency must improve without measurable CPU impact at scale. **Update:** README design highlights (latency claims). |
| 42 | 🟡 | **Add unprocessed_events.partition_id index** | The sequencer reads `unprocessed_events ORDER BY id LIMIT batch_size`. If partitioned sequencing is implemented, a composite index on `(partition_id, id)` would be needed. Even without partitioned sequencing, an index on `partition_id` could help the high_watermark UPDATE. **Benchmark gate:** Sequencer throughput must not decrease. **Update:** Schema migration docs. |

### 1D. Poll Path Optimization

The poll stored procedure is the most complex query in the system. At scale, it needs to efficiently find events across potentially thousands of cursors, acquire leases, and return results — all within a single procedure call.

| # | Sev | Item | Details |
|---|-----|------|---------|
| 43 | 🔴 | **Wire up adaptive polling_probability** | Replace the hardcoded `SELECT 0.001 AS polling_probability` (line 115) with a computed value based on subscription statistics. The formula from the README: `p = min(1, target_QPS / N_active)` converted to per-millisecond probability. Read `heartbeat_interval`, `active_consumers`, and `active_partitions` from `subscription_topics` and compute the appropriate probability. This is a correctness fix — the current hardcoded value means all consumers poll at the same rate regardless of cluster size. **Benchmark gate:** Correctness fix, regression acceptable. **Update:** README polling section, client protocol docs, stored procedure comments. |
| 44 | 🟠 | **Update subscription_topics statistics during poll** | The `active_partitions`, `active_consumers`, and `heartbeat_interval` columns in `subscription_topics` need to be refreshed periodically for adaptive polling to work. Add lightweight statistics refresh to the poll procedure (or to a separate scheduled procedure that runs frequently). Recompute: `active_consumers` = COUNT of consumers with valid heartbeats, `active_partitions` = COUNT of cursors where `high_watermark > position`, `heartbeat_interval` = `active_consumers / target_QPS`. **Benchmark gate:** Poll latency overhead must be <5% vs. current. **Update:** README subscription statistics section. |
| 45 | 🟠 | **Reduce lease lock contention** | The current poll acquires leases via INSERT ON DUPLICATE KEY UPDATE followed by an UPDATE. Under high contention (many consumers, few active partitions), this creates row-lock contention on `consumer_leases`. Evaluate: (a) SELECT ... FOR UPDATE SKIP LOCKED pattern for lease acquisition (MySQL 8.0+), (b) optimistic locking with version column, (c) reducing lock duration from 3s to a computed value based on expected processing time. **Benchmark gate:** Poll throughput under contention (10+ consumers, 100 partitions) must improve. **Update:** Stored procedure comments, architecture decisions. |
| 46 | 🟠 | **Eliminate wasted work in poll under contention** | Currently, Step 1 selects candidates into a temp table, then Step 2 tries to lock them. If another consumer already locked those cursors between the two steps, the work is wasted. Consider using `SELECT ... FOR UPDATE SKIP LOCKED` directly in the candidate CTE to atomically select only unlocked cursors. This eliminates the race window entirely. **Benchmark gate:** Poll throughput under 10+ concurrent consumers must improve or stay neutral. **Update:** Stored procedure comments. |
| 47 | 🟠 | **Remove FORCE INDEX hint** | The `FORCE INDEX (idx_sequences__partition_sequence)` on line 49 is fragile. Replace with query structure that naturally leads the optimizer to the correct plan (e.g., explicit covering index hints in the WHERE clause ordering). Verify with EXPLAIN that the correct index is still used. **Benchmark gate:** Poll latency must not increase (verify via EXPLAIN plans and benchmark). **Update:** Stored procedure comments. |
| 48 | 🟡 | **Optimize JSON_CONTAINS for topic filtering** | Line 58 uses `JSON_CONTAINS(v_topic_ids, CAST(c.topic_id AS JSON), '$')` to filter cursors by topic. For consumers subscribed to many topics, this is evaluated per cursor row. Evaluate: (a) joining against a temp table of topic_ids instead, (b) pre-filtering cursors by subscription_id + topic_id using an index. **Benchmark gate:** Poll latency for consumers with 10+ topics must improve. **Update:** Stored procedure comments. |
| 49 | 🟡 | **Make poll batch size configurable** | Currently hardcoded to 100. For high-throughput consumers, a larger batch size reduces poll frequency and amortizes the per-poll overhead. For low-latency consumers, a smaller batch size reduces processing delay. Allow the client to pass a batch size hint, clamped to [1, 10000]. **Benchmark gate:** Throughput per consumer must scale roughly linearly with batch size up to saturation. **Update:** Client protocol docs, README. |
| 50 | 🟡 | **Evaluate pre-computed cursor counts for high_watermark filtering** | The `p.high_watermark > c.position` check on line 65 is evaluated per cursor. For subscriptions with thousands of cursors but few active ones, this scans many inactive rows. Consider maintaining a precomputed `has_pending` flag on cursors, updated by the sequencer when high_watermark changes. **Benchmark gate:** Poll latency with 1000+ cursors (90% idle) must improve. **Update:** Schema docs, stored procedure comments. |

### 1E. Commit Path Optimization

Commits are less latency-sensitive than polls (batched by design), but at 1M events/sec they still represent significant write load.

| # | Sev | Item | Details |
|---|-----|------|---------|
| 51 | 🟠 | **Benchmark commit throughput ceiling** | Establish how many cursor position updates/sec the current `sp_cursors__commit` can sustain. The procedure creates a temp table, parses JSON, validates, then does a JOIN UPDATE. **Update:** `docs/benchmarks.md`. |
| 52 | 🟡 | **Evaluate batched cursor UPDATE without temp table** | For small commit batches (1–10 cursors), the overhead of CREATE TEMPORARY TABLE + INSERT + JOIN UPDATE may exceed a simple multi-row UPDATE. Evaluate a threshold-based approach: small batches use direct UPDATE, large batches use the temp table path. **Benchmark gate:** Commit latency for typical batch sizes (1–10 cursors) must improve. **Update:** Stored procedure comments. |
| 53 | 🟡 | **Release leases on commit** | Currently, committing cursor positions doesn't release the corresponding leases — they expire after the lock duration. Adding `UPDATE consumer_leases SET locked_until = NULL WHERE cursor_id IN (committed cursors) AND consumer_id = p_consumer_id` to the commit procedure would free leases immediately for other consumers. **Benchmark gate:** Correctness improvement; poll throughput under contention should improve as a side effect. **Update:** Client protocol docs, stored procedure comments. |

### 1F. Schema-Level Performance

| # | Sev | Item | Details |
|---|-----|------|---------|
| 54 | 🟠 | **Benchmark InnoDB vs. alternative storage for unprocessed_events** | `unprocessed_events` is a transient queue table with high INSERT/DELETE churn. Evaluate whether `ROW_FORMAT=COMPRESSED` or other InnoDB tuning reduces I/O. (MEMORY engine isn't viable here because it must survive MySQL restart.) **Benchmark gate:** Sequencer throughput must improve or stay neutral. **Update:** Schema docs, configuration tuning guide. |
| 55 | 🟠 | **Evaluate events table compression** | Events store `LONGBLOB` payloads. If payloads are typically JSON, InnoDB page compression (`COMPRESSION='zstd'` on MySQL 8.0+) could significantly reduce I/O for both writes and reads. **Benchmark gate:** Publish and poll throughput must not decrease; disk I/O should decrease. **Update:** Configuration tuning guide. |
| 56 | 🟡 | **Tune AUTO_INCREMENT lock mode** | MySQL's `innodb_autoinc_lock_mode=2` (interleaved) allows concurrent inserts to the `events` and `sequences` tables without table-level AUTO_INCREMENT locks. Verify this is set and document it as a requirement. **Benchmark gate:** Multi-threaded publish throughput should benefit. **Update:** Configuration tuning guide, prerequisites docs. |
| 57 | 🟡 | **Evaluate covering indexes for poll query** | The poll query joins cursors → partitions → sequences → consumer_leases → events. Ensure covering indexes exist so the JOIN doesn't require row lookups. Specifically: cursors should have a covering index for (subscription_id, topic_id, partition_id, random_key, position), and consumer_leases for (cursor_id, consumer_id, locked_until). **Benchmark gate:** Poll latency p99 must improve or stay neutral. **Update:** Schema migration docs. |

### 1G. Consumer GC & Dead Consumer Detection

| # | Sev | Item | Details |
|---|-----|------|---------|
| 58 | 🟠 | **Benchmark consumer_gc under load** | With 1000+ consumers, the `DELETE FROM consumers WHERE heartbeat_deadline <= v_timestamp` could lock many rows. Measure the impact on poll latency during GC runs. **Benchmark gate:** Establish baseline. **Update:** `docs/benchmarks.md`. |
| 59 | 🟡 | **Batch consumer GC with LIMIT** | Instead of deleting all expired consumers in one transaction, process in batches (e.g. `DELETE ... LIMIT 100`) to reduce lock hold time and avoid blocking concurrent polls. **Benchmark gate:** Poll p99 latency during GC must not spike. **Update:** Stored procedure comments. |
| 60 | 🟡 | **Cascade lease cleanup on consumer deletion** | Consumer deletion cascades to `consumer_leases` via FK. With many leases per consumer, this cascade can be slow. Consider explicit batch deletion of leases before deleting the consumer row. **Benchmark gate:** GC duration with consumers holding 100+ leases must improve. **Update:** Stored procedure comments. |

### 1H. Documentation Requirements

Every item above includes a documentation update. Additionally:

| # | Sev | Item | Details |
|---|-----|------|---------|
| 61 | 🟠 | **Create `docs/benchmarks.md`** | Living document with: hardware specs per profile, MySQL configuration per profile, baseline numbers (v0), and updated numbers after each optimization. Include methodology: warm-up runs, run count, statistical significance. |
| 62 | 🟠 | **Add performance section to README** | Summarize key performance characteristics: publish throughput, poll latency, end-to-end latency, and the conditions under which 1M events/sec is achievable. Reference `docs/benchmarks.md` for detailed numbers. |
| 63 | 🟠 | **Create configuration tuning guide** | Document all performance-relevant MySQL settings (`innodb_flush_log_at_trx_commit`, `innodb_autoinc_lock_mode`, buffer pool size, etc.), Boxy-specific settings (batch sizes, heartbeat intervals, lease durations), and HikariCP pool tuning. Include recommended values for different deployment sizes. |
| 64 | 🟡 | **Document performance testing methodology** | How to run benchmarks, how to interpret results, what constitutes a regression, and the PR gate criteria (all benchmarks must pass, no p99 regression >10% without justification). |

---

## 1. CI/CD & Build Infrastructure

| # | Sev | Item | Issue |
|---|-----|------|-------|
| 28 | 🔴 | Create GitHub Actions CI pipeline (build, test, lint on every push/PR) | — |
| 29 | 🔴 | Add integration test job using Testcontainers with MySQL in CI | — |
| 30 | 🟠 | Configure Maven release plugin and publish to Maven Central or GitHub Packages | — |
| 31 | 🟠 | Add dependency vulnerability scanning (Dependabot or Snyk) | — |
| 32 | 🟡 | Add code coverage reporting (JaCoCo) with minimum threshold enforcement | — |
| 33 | 🟢 | Add `.editorconfig` for consistent code style across contributors | — |

---

## 2. Stored Procedure Error Handling

| # | Sev | Item | Issue |
|---|-----|------|-------|
| 34 | 🔴 | Add EXIT HANDLER with ROLLBACK to `sp_events__publish.sql` (core publish path is unprotected) | — |
| 35 | 🔴 | Add EXIT HANDLER with ROLLBACK to `sp_events__publish_advanced.sql` | — |
| 36 | 🔴 | Add EXIT HANDLER with ROLLBACK to `sp_events__publish_multi.sql` (loops through JSON without per-item error handling) | — |
| 37 | 🟠 | Add EXIT HANDLER to `sp_sequence.sql` (batch sequencing path has no rollback strategy) | — |
| 38 | 🟠 | Add EXIT HANDLER to `sp_sequence_loop.sql` (loops until timeout with no error recovery) | — |
| 39 | 🟠 | Add EXIT HANDLER to `sp_consumers__gc.sql` (silent failure leaves dead consumers blocking partitions) | — |
| 40 | 🟡 | Add error handlers to `sp_subscriptions__create`, `sp_subscriptions__delete`, `sp_subscriptions__subscribe`, `sp_subscriptions__unsubscribe` | — |
| 41 | 🟡 | Add error handler to `sp_topics__delete.sql` | — |
| 42 | 🟡 | Add error handler to `sp_events__poll.sql` (complex procedure with no top-level handler) | — |

---

## 3. Observability & Monitoring

| # | Sev | Item | Issue |
|---|-----|------|-------|
| 43 | 🟠 | Add Micrometer metrics integration for publish latency, poll latency, commit latency, and throughput | — |
| 44 | 🟠 | Instrument event processing lag (high_watermark minus cursor position per subscription) | — |
| 45 | 🟠 | Add structured logging via `logback.xml` with JSON formatter for production environments | — |
| 46 | 🟠 | Add logging statements to all repository methods (currently zero logging in main code) | — |
| 47 | 🟠 | Add metrics for sequencer execution (events processed per run, duration, failures) | — |
| 48 | 🟠 | Add metrics for `consumer_gc` execution (consumers reaped per run, duration) | — |
| 49 | 🟡 | Expose HikariCP connection pool metrics via Micrometer (connections active, idle, pending) | — |
| 50 | 🟡 | Add health check endpoint or utility for database connectivity and sequencer liveness | — |
| 51 | 🟢 | Document recommended Grafana dashboards or alerting thresholds | — |

---

## 4. Configuration Externalization

| # | Sev | Item | Issue |
|---|-----|------|-------|
| 52 | 🟠 | Make HikariCP pool size configurable via environment variable (currently hardcoded to 10) | — |
| 53 | 🟡 | Make consumer lease lock duration configurable (currently hardcoded to 3s in `sp_events__poll.sql`) | — |
| 54 | 🟡 | Make polling batch size configurable (currently hardcoded to 100 in `sp_events__poll.sql`) | — |
| 55 | 🟡 | Make sequencer batch size configurable (currently hardcoded to 1000 in sequencer event) | — |
| 56 | 🟡 | Make default `heartbeat_interval` configurable (currently 15.0s in `subscription_topics` default) | — |
| 57 | 🟡 | Compute `polling_probability` dynamically from subscription statistics (currently hardcoded to 0.001) | — |

---

## 5. Schema & Data Management

| # | Sev | Item | Issue |
|---|-----|------|-------|
| 58 | 🟠 | Add `created_at` timestamp column to `events` table for time-based retention queries | [#7](https://github.com/rbilleci/boxy/issues/7) |
| 59 | 🟠 | Add index on `events.created_at` to support efficient cleanup without full table scans | [#7](https://github.com/rbilleci/boxy/issues/7) |
| 60 | 🟠 | Document and provide example retention/cleanup stored procedures or scripts | [#7](https://github.com/rbilleci/boxy/issues/7) |
| 61 | 🟡 | Add index on `unprocessed_events.partition_id` for efficient sequencer lookups | — |
| 62 | 🟡 | Add maximum event payload size validation in publish stored procedures | — |
| 63 | 🟡 | Add maximum array length validation in `sp_events__publish_multi.sql` | — |
| 64 | 🟢 | Consider adding `event_type` or `content_type` metadata column to `events` table | — |
| 65 | 🟡 | Evaluate partitioning the `events` table by key and/or time for scale | [#6](https://github.com/rbilleci/boxy/issues/6) |
| 66 | 🟡 | Evaluate pros/cons of normalization of the data model | [#94](https://github.com/rbilleci/boxy/issues/94) |
| 67 | 🟡 | Evaluate partial indexes for performance | [#60](https://github.com/rbilleci/boxy/issues/60) |

---

## 6. Security & Input Validation

| # | Sev | Item | Issue |
|---|-----|------|-------|
| 68 | 🟠 | Replace manual JSON construction in `ConsumerRepository.toJsonArray()` with a JSON library | — |
| 69 | 🟠 | Replace manual JSON construction in `CursorRepository.toJsonObject()` with a JSON library | — |
| 70 | 🟡 | Add input length validation for namespace names (enforced at Java layer, not just DB constraints) | — |
| 71 | 🟡 | Add input validation for topic partition counts (positive, within documented limits) | — |
| 72 | 🟢 | Add namespace/topic name character validation (reject control characters, null bytes) | — |

---

## 7. Java Core Resilience

| # | Sev | Item | Issue |
|---|-----|------|-------|
| 73 | 🟠 | Distinguish retryable errors (connection loss, lock timeout) from fatal errors (constraint violation) in `DataAccessException` | — |
| 74 | 🟠 | Add retry logic with exponential backoff for transient database failures in repository layer | — |
| 75 | 🟡 | Add circuit breaker pattern for stored procedure calls to prevent cascade failures | — |
| 76 | 🟡 | Enrich `DataAccessException` with context (repository name, operation, parameters) | — |

---

## 8. Testing

| # | Sev | Item | Issue |
|---|-----|------|-------|
| 77 | 🟠 | Add error/edge-case integration tests (duplicate consumers, invalid topics, stale commits, unknown consumers) | — |
| 78 | 🟠 | Replace `Thread.sleep(1000)` sequencer waits with polling/retry-based assertions (fragile in slow CI) | — |
| 79 | 🟠 | Fix PostgreSQL teardown bug in `BaseIT` (tables list is created empty, never populated from ResultSet) | — |
| 80 | 🟡 | Add concurrent consumer registration/polling tests to verify lease correctness under contention | — |
| 81 | 🟡 | Add event ordering guarantee tests across partitions | — |
| 82 | 🟡 | Add consumer lease expiration and rebalancing tests | — |
| 83 | 🟡 | Add unit tests for mapper classes and JSON construction utilities | — |
| 84 | 🟢 | Add large-scale stress tests (thousands of topics/partitions, hundreds of consumers) | — |

---

## 9. Background Event Reliability

| # | Sev | Item | Issue |
|---|-----|------|-------|
| 85 | 🟠 | Add error logging/alerting mechanism for sequencer event failures (currently silent) | — |
| 86 | 🟠 | Add error logging/alerting mechanism for `consumer_gc` event failures (currently silent) | — |
| 87 | 🟠 | Add a liveness check or monitoring query for sequencer (detect stalled sequencing) | — |
| 88 | 🟠 | Add a liveness check for `consumer_gc` (detect accumulation of dead consumers) | — |
| 89 | 🟡 | Evaluate moving tmp event record deletion to a separate scheduled event thread | [#93](https://github.com/rbilleci/boxy/issues/93) |
| 90 | 🟡 | Consider pros/cons of removing lease cleanup to a separate thread | [#57](https://github.com/rbilleci/boxy/issues/57) |
| 91 | 🟡 | Make sequencer interval configurable (currently hardcoded to 1 MINUTE) | — |
| 92 | 🟡 | Make `consumer_gc` interval configurable (currently hardcoded to 1 MINUTE) | — |

---

## 10. PostgreSQL Support

| # | Sev | Item | Issue |
|---|-----|------|-------|
| 93 | 🟡 | Port all 16 stored procedures from MySQL to PL/pgSQL | [#11](https://github.com/rbilleci/boxy/issues/11) |
| 94 | 🟡 | Port all 5 functions to PostgreSQL | [#11](https://github.com/rbilleci/boxy/issues/11) |
| 95 | 🟡 | Create PostgreSQL schema DDL (data types, generated columns, MEMORY engine alternatives) | [#11](https://github.com/rbilleci/boxy/issues/11) |
| 96 | 🟡 | Port sequencer and `consumer_gc` from MySQL events to `pg_cron` or application-level scheduling | [#11](https://github.com/rbilleci/boxy/issues/11) |
| 97 | 🟡 | Create PostgreSQL Liquibase changelog files | [#11](https://github.com/rbilleci/boxy/issues/11) |
| 98 | 🟡 | Add PostgreSQL Testcontainers integration test profile | [#11](https://github.com/rbilleci/boxy/issues/11) |
| 99 | 🟡 | Benchmark and optimize JDBC settings for PostgreSQL in production-class cloud deployment | [#54](https://github.com/rbilleci/boxy/issues/54) |
| 100 | 🟢 | Alternatively: remove PostgreSQL references from README and `DataSourceProvider` if not planned | — |

---

## 11. Documentation & Developer Experience

| # | Sev | Item | Issue |
|---|-----|------|-------|
| 101 | 🟠 | Write an operational runbook (scaling, monitoring, troubleshooting, maintenance) | — |
| 102 | 🟠 | Document event retention strategy and provide cleanup procedure examples | [#7](https://github.com/rbilleci/boxy/issues/7) |
| 103 | 🟠 | Write a configuration tuning guide (pool sizes, batch sizes, heartbeat intervals) | — |
| 104 | 🟡 | Add Javadoc to all public classes, methods, and domain records | — |
| 105 | 🟡 | Add inline documentation to all stored procedures (parameters, behavior, error codes) | — |
| 106 | 🟡 | Publish a quickstart example project demonstrating producer and consumer integration | — |
| 107 | 🟢 | Add architecture decision records (ADRs) for key design choices | — |
| 108 | 🟢 | Create a CHANGELOG.md for tracking version history | — |

---

## 12. Packaging & Distribution

| # | Sev | Item | Issue |
|---|-----|------|-------|
| 109 | 🟠 | Configure Maven Central or GitHub Packages publishing for all library modules | — |
| 110 | 🟠 | Add proper Maven metadata (description, URL, SCM, developers, license) to POMs | — |
| 111 | 🟡 | Version the project properly (remove `-SNAPSHOT` for release, set up semantic versioning) | — |
| 112 | 🟢 | Consider publishing a Docker image with pre-configured MySQL and Boxy schema for evaluation | — |

---

## 13. Branch & Release Hygiene

| # | Sev | Item | Issue |
|---|-----|------|-------|
| 113 | 🟠 | Merge current `refactor` branch into `main` and establish `main` as the primary branch | — |
| 114 | 🟡 | Clean up ~30 stale local branches (`codex/*`, `jetbrains-junie/*`) | — |
| 115 | 🟡 | Establish branch protection rules on `main` (require PR reviews, passing CI) | — |
| 116 | 🟢 | Tag releases and maintain a release process (GitHub Releases with changelogs) | — |

---

## 14. Feature: Client SDKs (from GitHub Issues)

| # | Sev | Item | Issue |
|---|-----|------|-------|
| 117 | 🟠 | Implement Worker/Consumer APIs (async, high-throughput event processing with back-pressure) | [#14](https://github.com/rbilleci/boxy/issues/14) |
| 118 | 🟡 | Support Go client SDK | [#44](https://github.com/rbilleci/boxy/issues/44) |
| 119 | 🟡 | Support Python client SDK | [#46](https://github.com/rbilleci/boxy/issues/46) |
| 120 | 🟡 | Support Rust client SDK | [#45](https://github.com/rbilleci/boxy/issues/45) |
| 121 | 🟡 | Support .NET client SDK | [#48](https://github.com/rbilleci/boxy/issues/48) |
| 122 | 🟡 | Support JavaScript/Node.js client SDK | [#49](https://github.com/rbilleci/boxy/issues/49) |

---

## 15. Feature: Core Enhancements (from GitHub Issues)

| # | Sev | Item | Issue |
|---|-----|------|-------|
| 123 | 🟠 | Support partition resizing (add/remove partitions from existing topics) | [#79](https://github.com/rbilleci/boxy/issues/79) |
| 124 | 🟠 | Review `topics_cache` for invalidation issues | [#77](https://github.com/rbilleci/boxy/issues/77) |
| 125 | 🟡 | Support subscribing from high watermark or position 0 | [#3](https://github.com/rbilleci/boxy/issues/3) |
| 126 | 🟡 | Review lease share distribution approach (Hamilton apportionment method) | [#58](https://github.com/rbilleci/boxy/issues/58) |
| 127 | 🟢 | Evaluate async JDBC drivers for worker | [#28](https://github.com/rbilleci/boxy/issues/28) |
| 128 | 🟢 | Evaluate goharvest design for applicable patterns | [#9](https://github.com/rbilleci/boxy/issues/9) |
| 129 | 🟢 | Examine Bento approaches, pipelines, and treating events as bytes | [#36](https://github.com/rbilleci/boxy/issues/36) |
| 130 | 🟡 | MySQL: Benchmark and optimize JDBC settings in production-class cloud deployment | [#2](https://github.com/rbilleci/boxy/issues/2) |

---

## Recommended Execution Order

The plan is structured in two phases of foundational work, followed by parallel workstreams:

1. **Phase 0A: Agent optimization** — Create `CLAUDE.md`, `AGENTS.md`, `CONTRIBUTING.md` so that every subsequent task done by an agent (or a human) starts from a clear foundation.
2. **Phase 0B: JDK 25 upgrade** — Upgrade compiler targets and validate dependencies before writing new code.
3. **Phase 0C: Module restructuring** — Consolidate into `boxy-core` (all implementations), `boxy-db` (migrations), `boxy-cli` (CLI), `boxy-test` (shared test infra).
4. **Phase 0D: CLI implementation** — Build out `boxy-cli` with GraalVM native-image, targeting macOS ARM first. Validates the module boundaries from 0C.
5. **Phase 1A: Benchmark infrastructure** — Build end-to-end and component benchmarks. Establish baseline numbers on all hardware profiles. _This must be done before any performance optimization._
6. **Phase 1B–1G: Performance optimization** — Work through publish, sequencer, poll, commit, schema, and GC optimizations. Each change requires a benchmark gate. Can be parallelized across contributors but each PR must include benchmark results.
7. **CI/CD** (Section 1) — Wire up GitHub Actions with benchmark regression checks.
8. **Everything else** (Sections 2–15) — Can be parallelized across contributors/agents. Stored procedure error handling and observability are the highest-impact items remaining.

### Performance change discipline

Every PR that touches a performance-sensitive path must include:
- **Before:** Benchmark results from the `bench-local` profile on the PR's base branch
- **After:** Benchmark results from the `bench-local` profile on the PR's head branch
- **Verdict:** "Improvement" (merge), "Neutral" (merge), "Regression with justification" (merge if correctness fix), or "Regression" (do not merge)
- **Documentation update:** Updated numbers in `docs/benchmarks.md` and any affected README/guide sections

---

## Proposed Module Structure (Post-Restructuring)

```
boxy/
├── pom.xml                          # Parent POM (JDK 25, dependency management)
├── boxy-db/                         # Liquibase migrations + SQL
│   └── src/main/resources/db/changelog/
│       ├── mysql/                   # MySQL DDL and stored procedures
│       └── pgsql/                   # PostgreSQL DDL and stored procedures
├── boxy-core/                       # Domain records, repositories, implementations
│   └── src/main/java/boxy/core/
│       ├── domain/                  # Consumer, Cursor, Event, Namespace, etc.
│       ├── repository/              # JDBC repositories (MySQL/PostgreSQL support)
│       ├── mapper/                  # ResultSet mappers
│       ├── metrics/                 # BoxyMeterRegistry, HealthCheck
│       ├── retry/                   # RetryPolicy, CircuitBreaker
│       ├── security/                # InputValidator
│       ├── util/                    # JsonUtils
│       ├── worker/                  # Worker, WorkerConfig, EventHandler
│       ├── DataSourceProvider.java  # HikariCP pool factory
│       └── DataAccessException.java
├── boxy-cli/                        # Native CLI (GraalVM)
│   └── src/main/java/boxy/cli/
│       ├── BoxyCommand.java         # Main entry point (picocli)
│       ├── db/                      # boxy db init|migrate|status
│       ├── event/                   # boxy event publish|listen
│       ├── namespace/               # boxy namespace create|delete|rename|move
│       ├── topic/                   # boxy topic create|delete
│       └── subscription/            # boxy subscription create|delete|subscribe|unsubscribe
└── boxy-test/                       # Shared test infrastructure
    └── src/main/java/boxy/test/
        ├── BaseIT.java
        └── TestData.java
├── CLAUDE.md                        # Agent context file
├── AGENTS.md                        # Agent workflow guide
├── CONTRIBUTING.md                  # Contributor guide
└── PRODUCTION_READINESS.md          # This file
```

---

## Appendix: GitHub Issues Cross-Reference

All open issues mapped to plan items above.

| Issue | Title | Plan Item(s) |
|-------|-------|-------------|
| [#2](https://github.com/rbilleci/boxy/issues/2) | MySQL: Benchmark and optimize JDBC settings | #130 |
| [#3](https://github.com/rbilleci/boxy/issues/3) | Subscribe from high watermark or 0 | #125 |
| [#6](https://github.com/rbilleci/boxy/issues/6) | Event table partitioning | #65 |
| [#7](https://github.com/rbilleci/boxy/issues/7) | Periodic event cleanup | #58, #59, #60, #102 |
| [#9](https://github.com/rbilleci/boxy/issues/9) | Evaluate goharvest | #128 |
| [#11](https://github.com/rbilleci/boxy/issues/11) | Support PGSQL | #93–#99 |
| [#14](https://github.com/rbilleci/boxy/issues/14) | Implement Worker/Consumer APIs | #117 |
| [#28](https://github.com/rbilleci/boxy/issues/28) | Evaluate async JDBC drivers | #127 |
| [#34](https://github.com/rbilleci/boxy/issues/34) | Implement batch event publish SP | Already implemented as `sp_events__publish_multi` — may be closable |
| [#36](https://github.com/rbilleci/boxy/issues/36) | Examine Bento approaches | #129 |
| [#37](https://github.com/rbilleci/boxy/issues/37) | Boxy CLI | #16–#27 |
| [#38](https://github.com/rbilleci/boxy/issues/38) | Relocate code to Worker/Producer APIs | #11–#15 (module restructuring) |
| [#44](https://github.com/rbilleci/boxy/issues/44) | Support Go | #118 |
| [#45](https://github.com/rbilleci/boxy/issues/45) | Support Rust | #120 |
| [#46](https://github.com/rbilleci/boxy/issues/46) | Support Python | #119 |
| [#48](https://github.com/rbilleci/boxy/issues/48) | Support .NET | #121 |
| [#49](https://github.com/rbilleci/boxy/issues/49) | Support JavaScript | #122 |
| [#54](https://github.com/rbilleci/boxy/issues/54) | PGSQL: Benchmark JDBC settings | #99 |
| [#56](https://github.com/rbilleci/boxy/issues/56) | Optimize project for agents | #1–#5 |
| [#57](https://github.com/rbilleci/boxy/issues/57) | Lease cleanup to separate thread | #90 |
| [#58](https://github.com/rbilleci/boxy/issues/58) | Review lease share distribution | #126 |
| [#60](https://github.com/rbilleci/boxy/issues/60) | Partial indexes | #67 |
| [#77](https://github.com/rbilleci/boxy/issues/77) | Review topics cache invalidation | #124 |
| [#79](https://github.com/rbilleci/boxy/issues/79) | Support partition resizing | #123 |
| [#93](https://github.com/rbilleci/boxy/issues/93) | Move tmp event deletion to separate thread | #89 |
| [#94](https://github.com/rbilleci/boxy/issues/94) | Evaluate normalization of model | #66 |

> **Note:** Issue #34 (Batch Event Publish SP) appears to already be implemented as `sp_events__publish_multi.sql` and may be closable.
