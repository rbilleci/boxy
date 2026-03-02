# Boxy — Agent Workflow Guide

This document defines how AI agents (Claude, Codex, Junie, Cursor, etc.) should interact
with this repository. It supplements `CLAUDE.md` (project context) and `CONTRIBUTING.md`
(general contributor guide).

---

## Before You Start Any Task

1. **Read `CLAUDE.md`** — understand the project architecture, conventions, and critical decisions.
2. **Check `PRODUCTION_READINESS.md`** — find the item number you're working on and understand its full requirements.
3. **Check `CHANGELOG.md`** — confirm the item hasn't already been completed or is in progress.
4. **Check the current branch** — confirm you're on the right branch (see Branch Naming below).

---

## Branch Naming

All agent-created branches must follow this pattern:

```
<agent-prefix>/<short-description>
```

| Agent | Prefix |
|-------|--------|
| Claude / Claude Code | `codex/` |
| JetBrains Junie | `junie/` |
| Cursor | `cursor/` |
| GitHub Copilot | `copilot/` |
| Human contributors | `feat/`, `fix/`, `chore/`, `docs/` |

**Examples:**
```
codex/add-adaptive-polling-probability
junie/fix-pgsql-teardown-bug
cursor/implement-boxy-cli-module
feat/support-partition-resizing
fix/sp-publish-error-handler
```

Branch names should be lowercase, hyphen-separated, and descriptive enough to identify
the work at a glance.

---

## Commit Message Format

All commits must follow this format:

```
<type>(<scope>): <short description> [item #N]

<optional body — explain the "why", not the "what">

Closes: <issue reference or PRODUCTION_READINESS.md item>
Refs: #<github-issue-number> (if applicable)

Co-Authored-By: <Agent Name> <noreply@anthropic.com>
```

**Types:**
- `feat` — new feature or capability
- `fix` — bug fix (correctness, not performance)
- `perf` — performance improvement (must include benchmark results in body)
- `refactor` — code restructuring without behavior change
- `test` — test additions or fixes
- `docs` — documentation only
- `chore` — build system, dependencies, tooling

**Scopes (examples):** `agents`, `cli`, `core`, `mysql`, `pgsql`, `db`, `benchmark`, `ci`

**Example:**
```
perf(mysql): implement true batch publish in sp_events__publish_multi [item #32]

Replaced the N-transaction loop with a single bulk INSERT transaction.
Before: 12,400 events/sec (10 threads, batch=100)
After:  89,300 events/sec (10 threads, batch=100) — 7.2x improvement

Benchmark: bench-local profile, Testcontainers MySQL 8.0.43, tmpfs

Closes: Phase 1B, item 32 of PRODUCTION_READINESS.md

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
```

---

## Pull Request Requirements

Every PR must include:

### Title
Matches the commit subject: `<type>(<scope>): <description> [item #N]`

### Description
- Link to the `PRODUCTION_READINESS.md` item being addressed
- Summary of changes made
- For **performance changes**: before/after benchmark numbers (see below)
- For **schema changes**: migration strategy and rollback plan
- For **stored procedure changes**: EXPLAIN plan comparison if query structure changed
- Updated `CHANGELOG.md` entry

### Benchmark requirement (performance PRs only)
```
## Benchmark Results

**Profile:** bench-local (Testcontainers, tmpfs MySQL 8.0.43)
**Hardware:** <describe machine>

| Metric | Before | After | Delta |
|--------|--------|-------|-------|
| Publish throughput (events/sec) | X | Y | +Z% |
| Poll p99 latency (ms) | X | Y | -Z% |
| ... | | | |

**Verdict:** Improvement / Neutral / Regression (with justification)
```

---

## Stored Procedure Rules

These rules are non-negotiable. Violating them will cause the PR to be rejected.

### Every write procedure MUST have an error handler
```sql
CREATE PROCEDURE sp_example(...)
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;
    -- ... your writes ...
    COMMIT;
END;
```

### Never modify an existing Liquibase changeset
Schema migrations are immutable once applied. To change a stored procedure, add a new
changeset that drops and recreates it. Example:

```xml
<changeSet id="2026-03-15-update-sp-example" author="agent">
    <sqlFile path="mysql/sp/sp_example.sql"
             relativeToChangelogFile="true"
             splitStatements="false"
             endDelimiter="//">
    </sqlFile>
</changeSet>
```

### Comment every parameter and return value
```sql
-- sp_events__poll
-- Polls for events assigned to the given consumer.
--
-- Parameters:
--   p_consumer_id (VARCHAR(36)): The consumer's UUID. Must be registered.
--
-- Result sets:
--   1. Events: cursor_id, partition_id, sequence, event_id, data
--   2. Metadata: polling_probability (DOUBLE, per-millisecond)
--
-- Error codes:
--   UNKNOWN_CONSUMER: consumer_id is not registered or has expired
```

### FORCE INDEX is banned in new code
Do not add new `FORCE INDEX` hints. If the optimizer makes a bad choice, restructure the query
or add a covering index. Document the EXPLAIN plan in the PR.

---

## Java Rules

### Don't add framework dependencies
Boxy has no Spring, no Guice, no Quarkus. New dependencies require explicit discussion.
Allowed: SLF4J, Logback, HikariCP, Liquibase, MySQL Connector/J, HdrHistogram, picocli (CLI only).

### Repository pattern — no raw SQL in non-repository classes
All JDBC calls go through repository classes extending `BaseRepository`. No inline SQL
in domain classes, mappers, or CLI commands.

### Manual JSON construction is banned
Do not use string concatenation to build JSON. Use a JSON library (Jackson or the JDK's
built-in `javax.json` / `java.util.Map` + `JSON.toJson` pattern once established).

### Update Javadoc with every change
Every public method and class must have Javadoc describing its purpose, parameters,
return values, and exceptions thrown.

---

## Test Rules

### Every new stored procedure needs an integration test
Add a test in `boxy-core/src/test/java/boxy/core/it/` that exercises the new procedure.

### No `Thread.sleep()` in tests
Use polling with a timeout (e.g. `Awaitility`) instead of fixed sleeps. Fixed sleeps cause
flaky tests in slow CI environments.

### Tests must clean up after themselves
The `@AfterEach` in `BaseIT` truncates all tables. Do not leave test data that breaks
subsequent tests within the same JVM run.

### Integration tests use the shared `TestData` seed
Call `TestData.seed(dataSource)` in `@BeforeEach` to get the standard namespace/topic/
subscription structure rather than creating one-off fixtures.

---

## Documentation Update Checklist

After completing any item, update the following as appropriate:

- [ ] `CHANGELOG.md` — add entry with date, item #, description, commit hash
- [ ] `PRODUCTION_READINESS.md` — mark item as done in the status table (if maintaining)
- [ ] `README.md` — update if user-facing behavior changed
- [ ] `docs/benchmarks.md` — update if performance changed (create file if it doesn't exist)
- [ ] `docs/configuration-tuning.md` — update if new configuration options added
- [ ] Stored procedure file — update inline comments
- [ ] Javadoc — update for changed Java classes

---

## What Agents Should NOT Do

- ❌ Push directly to `main` or `refactor` — always use a feature branch and open a PR
- ❌ Delete or modify existing Liquibase changesets
- ❌ Add `Thread.sleep()` in tests
- ❌ Add Spring, Quarkus, or other framework dependencies without discussion
- ❌ Add `FORCE INDEX` hints in new SQL
- ❌ Use string concatenation to build SQL or JSON
- ❌ Commit broken tests — all tests must pass before committing
- ❌ Commit large binary files
- ❌ Make performance changes without benchmark evidence
- ❌ Modify `LICENSE`

## What Agents SHOULD Do

- ✅ Read `CLAUDE.md` and `PRODUCTION_READINESS.md` before starting any work
- ✅ Create a feature branch before making any changes
- ✅ Update `CHANGELOG.md` after completing each item
- ✅ Include benchmark results in performance PRs
- ✅ Add an error handler to every stored procedure that writes data
- ✅ Update Javadoc alongside code changes
- ✅ Keep commits small and focused — one logical change per commit
- ✅ Add `Co-Authored-By` trailer to commits

---

## Getting Help

If a task is ambiguous or you encounter an unexpected state (e.g. conflicting requirements,
broken tests you didn't introduce, unclear schema migrations), stop and leave a descriptive
comment in the PR or issue rather than guessing. It is better to ask than to ship something
that has to be reverted.
