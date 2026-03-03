# Boxy — Claude Agent Context

This file gives AI agents (Claude, Codex, Junie, Cursor, etc.) the context needed to work
effectively in this repository. Read this before making any changes.

---

## What Is Boxy?

Boxy is a **multi-tenant event streaming library** built directly on top of a relational database.
It exposes Apache Pulsar-like semantics (topics, partitions, subscriptions, consumer groups,
cursor commits) over a transactional outbox — no Kafka, no Pulsar, no broker process to operate.

**Target users:** Teams running monolithic or modular-monolith applications on MySQL or PostgreSQL
who want event streaming without the operational overhead of a dedicated message broker.

**Key insight:** All mutation logic lives in database stored procedures. A client in *any* language
can implement the consumer protocol by calling four stored procedures: `register`, `poll`,
`commit`, `deregister`. The Java module is a reference implementation, not a required runtime.

---

## Repository Layout

```
boxy/
├── boxy-db/                 # Liquibase schema migrations + SQL (mysql/, pgsql/)
├── boxy-core/               # Java core: domain, repositories, metrics, worker, retry, security
├── boxy-cli/                # Native CLI application (GraalVM)
├── boxy-test/               # Shared test infrastructure (BaseIT, TestData)
├── PRODUCTION_READINESS.md  # Master plan — read before picking up any task
├── CHANGELOG.md             # Implementation log — update after every completed item
├── CLAUDE.md                # This file
├── AGENTS.md                # Agent-specific workflow rules
└── CONTRIBUTING.md          # Human + agent contributor guide
```

### boxy-db structure

```
boxy-db/src/main/resources/db/changelog/
├── db.changelog-master.xml         # Liquibase root (delegates to DB-specific changelogs)
└── mysql/
    ├── mysql.changelog-master.xml  # MySQL changelog (runs schema, then SPs, functions, events)
    ├── mysql.schema.sql            # DDL: all tables and indexes
    ├── sp/                         # Stored procedures (sp_<entity>__<action>.sql)
    ├── fn/                         # Functions (fn_<name>.sql)
    └── events/                     # MySQL scheduled events (sequencer, consumer_gc)
```

### boxy-core structure

```
boxy-core/src/main/java/boxy/core/
├── domain/           # Java records: Consumer, Cursor, Event, Namespace, Partition,
│                     #   Subscription, SubscriptionTopic, Topic, ConsumerLease
├── mapper/           # RowMapper implementations for each domain record
├── repository/       # JDBC repositories: one class per domain entity
│   └── BaseRepository.java   # Shared execute/query/update helpers
├── metrics/          # BoxyMeterRegistry, HealthCheck
├── retry/            # RetryPolicy, CircuitBreaker
├── security/         # InputValidator
├── util/             # JsonUtils
├── worker/           # Worker, WorkerConfig, EventHandler, PolledEvent
├── DataSourceProvider.java   # HikariCP pool factory (env-var configured)
└── DataAccessException.java  # Runtime wrapper around SQLException
```

---

## Key Domain Concepts

| Concept | Description |
|---------|-------------|
| **Namespace** | Hierarchical path-based container (e.g. `tenant-a/payments`). Uses a closure table for ancestry queries. |
| **Topic** | Belongs to a namespace. Declares a fixed partition count (1–1024, max 65536). |
| **Partition** | A shard of a topic. ID is computed as `(topic_id << 16) + partition_number`. Tracks a `high_watermark` (highest sequenced event number). |
| **Event** | Raw payload (`LONGBLOB`). Published into `events` + `unprocessed_events`, then sequenced by the background sequencer. |
| **Sequence** | Per-partition monotonic event number. Assigned by `sp_sequence` background process. |
| **Subscription** | A named consumer group. Links to one or more topics via `subscription_topics`. |
| **Cursor** | Tracks consumption position per (subscription, partition). Has a persistent `random_key` for fair-share polling distribution. |
| **Consumer** | A registered client instance. Identified by a UUIDv4. Has a heartbeat deadline. |
| **Consumer Lease** | Short-lived lock (3s) associating a consumer with a cursor during active polling. |

## Data Flow

```
Publisher                  Database                    Consumer
─────────                  ────────                    ────────
publish(event)  ──────▶  events + unprocessed_events
                          ◀── sequencer (background) ──▶  sequences + high_watermark
                                                      ◀──  poll (consumer calls sp_events__poll)
                                                           process events
                                                      ──▶  commit (sp_cursors__commit)
```

---

## Build & Test

```bash
# Build everything (skip tests)
mvn clean package -DskipTests

# Run integration tests (requires Docker for Testcontainers)
mvn test

# Run Liquibase migrations against a local MySQL
mvn liquibase:update \
  -Dliquibase.url=jdbc:mysql://localhost:3306/events_db \
  -Dliquibase.username=user \
  -Dliquibase.password=password

# Build a specific module only
mvn clean package -pl boxy-core -DskipTests
```

**Prerequisites:** Java 25+, Maven 3.9+, Docker (for integration tests)

**Environment variables for tests:**

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_TYPE` | `mysql` | `mysql` or `postgres` |
| `DB_HOST` | `localhost` | Database host |
| `DB_PORT` | `3306`/`5432` | Database port |
| `DB_NAME` | `events_db` | Schema name |
| `DB_USER` | `user` | Database user |
| `DB_PASSWORD` | `password` | Database password |

---

## Coding Conventions

### Java
- **Java 25** — use modern features: records, sealed classes, pattern matching, text blocks
- **No ORM** — all database access via raw JDBC through `BaseRepository`
- **Records for domain** — all domain objects in `boxy.core.domain` are immutable Java records
- **No Spring** — the library has no framework dependencies; HikariCP and SLF4J only
- **Checked exceptions** — catch `SQLException`, wrap in `DataAccessException` (unchecked)
- **Package structure** — `boxy.core.*` for core, `boxy.cli.*` for CLI, `boxy.test.*` for shared test infrastructure
- **Naming** — repositories are `XxxRepository`, mappers are `XxxMapper`, domains are `Xxx`

### SQL / Stored Procedures
- **One procedure per file** — filename matches procedure name exactly: `sp_consumers__register.sql`
- **Naming convention** — `sp_<entity>__<action>` (double underscore between entity and action)
- **Functions** — `fn_<name>.sql`, deterministic where possible
- **Error handling** — all procedures that write data MUST have an `EXIT HANDLER FOR SQLEXCEPTION` with `ROLLBACK` and `RESIGNAL`
- **No dynamic SQL** — parameterized only; no `CONCAT` of user input into SQL strings
- **MEMORY tables** — use for temporary working sets in stored procedures; always `DROP ... IF EXISTS` at start and end
- **Comments** — every procedure must document: parameters, return values, error codes it signals

### Liquibase
- **DB-specific changelogs** — never use Liquibase's DB-agnostic abstractions; maintain hand-crafted SQL per DB
- **Stored procedures in changelogs** — use `<sqlFile>` with `splitStatements="false"` and `endDelimiter="//"`
- **Never modify existing changesets** — add new changesets instead; existing ones are immutable

---

## Critical Architecture Decisions

1. **Stored procedures for all mutations** — clients in any language call the same SPs; the Java code is a reference impl, not the authoritative impl.

2. **Events are never deleted by Boxy** — event cleanup is the caller's responsibility. The `events` table will grow indefinitely without an external retention policy.

3. **Partition IDs are computed, not stored independently** — `partition_id = (topic_id << 16) + partition_number`. This means a topic can have at most 65536 partitions (practical limit: 1024).

4. **The sequencer is a single-threaded background process** — it converts `unprocessed_events` into sequenced rows. It is a potential bottleneck at very high throughput. See Phase 1 in `PRODUCTION_READINESS.md`.

5. **`topics_cache` is a MEMORY table** — it is lost on MySQL restart and repopulated on first cache miss. Do not rely on it for data durability.

6. **Case-sensitive names** — namespace and topic names are case-sensitive (`utf8mb4_bin` collation).

7. **Consumer IDs must be UUIDv4** — high-entropy, non-reusable. Never reuse a consumer ID after deregistration.

---

## Files Agents Should NOT Modify Without Discussion

- `boxy-db/src/main/resources/db/changelog/mysql/mysql.schema.sql` — schema changes require a new Liquibase changeset, not an edit to the base schema file
- Existing Liquibase changeset files — changesets are immutable once applied
- `LICENSE` — Apache 2.0, do not change
- `PRODUCTION_READINESS.md` — update item status only; do not restructure without discussion

## Files Agents Should Always Update Together

- A stored procedure file + its corresponding Liquibase changeset (if the SP is being replaced in a migration)
- A repository method + its mapper + its domain record (keep these in sync)
- Any performance change + `docs/benchmarks.md` (benchmark results must accompany perf changes)
- Any user-facing change + `README.md`

---

## Performance Target

**1M events/second** sustained throughput on production-grade hardware (AWS r6g.2xlarge class,
provisioned IOPS MySQL). All performance changes must be accompanied by benchmarks.
See Phase 1 of `PRODUCTION_READINESS.md` and `docs/benchmarks.md` (once created) for details.

---

## Current Status

The project is in active development on the `refactor` branch. The `main` branch should be
considered the stable reference. Before starting any task, check `PRODUCTION_READINESS.md`
for context and `CHANGELOG.md` to see what has already been done.
