# Contributing to Boxy

Thank you for your interest in contributing to Boxy! This guide covers everything you need
to contribute effectively, whether you're a human or an AI agent.

> **AI agents:** Also read [`AGENTS.md`](./AGENTS.md) for agent-specific workflow rules
> and [`CLAUDE.md`](./CLAUDE.md) for project architecture context.

---

## Table of Contents

- [Development Setup](#development-setup)
- [Project Structure](#project-structure)
- [Making Changes](#making-changes)
- [Code Style](#code-style)
- [Testing](#testing)
- [Submitting a Pull Request](#submitting-a-pull-request)
- [Performance Changes](#performance-changes)
- [Schema and SQL Changes](#schema-and-sql-changes)
- [Documentation](#documentation)

---

## Development Setup

### Prerequisites

| Tool | Minimum Version | Notes |
|------|----------------|-------|
| JDK | 25 | Temurin or GraalVM recommended |
| Maven | 3.9 | |
| Docker | 20+ | Required for integration tests (Testcontainers) |
| MySQL | 8.0+ | Only needed for manual testing against a real DB |

### Getting Started

```bash
# Clone the repository
git clone https://github.com/rbilleci/boxy.git
cd boxy

# Build (skipping tests for speed)
mvn clean package -DskipTests

# Run all integration tests (requires Docker)
mvn test

# Run tests for a specific module
mvn test -pl boxy-core
```

### Recommended IDE Setup

- **IntelliJ IDEA** — open the root `pom.xml` as a Maven project
- Enable annotation processing if prompted
- Set the project SDK to JDK 25

---

## Project Structure

See [`CLAUDE.md`](./CLAUDE.md) for a full description of modules, packages, and key design
concepts. In brief:

| Module | Purpose |
|--------|---------|
| `boxy-db` | Liquibase schema migrations and all SQL (stored procedures, functions, scheduled events) |
| `boxy-core` | Java domain records, JDBC repository implementations, connection pooling |

The project is being restructured into additional modules — see Phase 0C of
[`PRODUCTION_READINESS.md`](./PRODUCTION_READINESS.md) for the target layout.

---

## Making Changes

### 1. Find your task

Check [`PRODUCTION_READINESS.md`](./PRODUCTION_READINESS.md) for the current work backlog.
Check [`CHANGELOG.md`](./CHANGELOG.md) to see what's already been done.

### 2. Create a branch

Branch from the latest `refactor` branch:

```bash
git checkout refactor
git pull origin refactor
git checkout -b feat/your-feature-name
```

**Branch naming:**
- `feat/` — new features
- `fix/` — bug fixes
- `perf/` — performance improvements
- `refactor/` — structural changes without behavior change
- `docs/` — documentation only
- `chore/` — build, CI, dependency updates

### 3. Make your changes

Keep commits small and focused. One logical change per commit. See commit message format below.

### 4. Test your changes

```bash
# All tests must pass before opening a PR
mvn test

# Run a specific test class
mvn test -pl boxy-core -Dtest=EventPollIT

# Run with a specific DB type
DB_TYPE=mysql mvn test
```

### 5. Update documentation

Every PR should update:
- `CHANGELOG.md` — add an entry for your change
- `README.md` — if you changed user-facing behavior
- Javadoc — if you changed a public API
- Stored procedure comments — if you changed a stored procedure

---

## Code Style

### Java

- **Java 25** — use modern features where they improve clarity
- **Immutable domain objects** — use Java records for all domain classes
- **No framework dependencies** — Boxy has no Spring/Quarkus dependency; keep it that way
- **All DB access via repositories** — no inline SQL outside of `*Repository` classes
- **Exception handling** — catch `SQLException`, wrap in `DataAccessException`, never swallow
- **Logging** — use SLF4J; include relevant context in log messages (entity IDs, operation names)

```java
// Good
private static final Logger log = LoggerFactory.getLogger(ConsumerRepository.class);

public Optional<Consumer> find(String consumerId) {
    log.debug("Finding consumer id={}", consumerId);
    return queryOne("SELECT * FROM consumers WHERE id = ?", ConsumerMapper.INSTANCE, consumerId);
}

// Bad — no logging, no context
public Optional<Consumer> find(String id) {
    return queryOne("SELECT * FROM consumers WHERE id = ?", ConsumerMapper.INSTANCE, id);
}
```

### SQL / Stored Procedures

- **One file per procedure** — named exactly as the procedure: `sp_consumers__register.sql`
- **Double underscore** — entity and action separated by `__`: `sp_<entity>__<action>`
- **Error handlers on all write procedures** — see [`AGENTS.md`](./AGENTS.md) for the template
- **No `FORCE INDEX`** — use proper covering indexes and let the optimizer choose
- **Comment all parameters and return values** — see [`AGENTS.md`](./AGENTS.md) for format
- **MEMORY tables for temp state** — always `DROP TEMPORARY TABLE IF EXISTS` at start and end

### Commit Messages

```
<type>(<scope>): <short description> [item #N]

<optional body>

Closes: <reference>
Refs: #<issue> (if applicable)

Co-Authored-By: Name <email>
```

Types: `feat`, `fix`, `perf`, `refactor`, `test`, `docs`, `chore`

Scopes: `agents`, `cli`, `core`, `mysql`, `pgsql`, `db`, `benchmark`, `ci`

---

## Testing

### Integration Tests

Boxy's tests are integration tests using Testcontainers. They spin up a real MySQL instance
in Docker and run the full stack.

```bash
# Run all integration tests
mvn test

# Run a single test
mvn test -pl boxy-core -Dtest=EventPollIT#poll_returnsEventsAndLocksCursor
```

### Writing New Tests

- Extend `BaseIT` for integration tests
- Use `TestData.seed(dataSource)` for standard test fixtures
- **No `Thread.sleep()`** — use `Awaitility` or polling loops with timeouts
- Name test methods descriptively: `<method>_<scenario>_<expectedOutcome>`

```java
@Test
void poll_withExpiredLease_allowsAnotherConsumer() {
    // Arrange: register consumer A, poll (takes lease), let lease expire
    // Act: register consumer B, poll
    // Assert: consumer B receives the events
}
```

### Benchmark Tests

Benchmarks live alongside integration tests but are tagged separately. They use `HdrHistogram`
for latency recording. See `BenchmarkIT.java` for examples. All performance PRs must run
benchmarks and include results. See [`PRODUCTION_READINESS.md`](./PRODUCTION_READINESS.md)
Phase 1A for the benchmark framework being built.

---

## Submitting a Pull Request

1. Ensure all tests pass: `mvn test`
2. Update `CHANGELOG.md` with your entry
3. Open a PR against the `refactor` branch
4. Fill in the PR template:
   - What item(s) from `PRODUCTION_READINESS.md` does this address?
   - What changed and why?
   - Any risks or tradeoffs?
   - For perf PRs: benchmark results (see below)

### PR Review Checklist

- [ ] All tests pass
- [ ] `CHANGELOG.md` updated
- [ ] Documentation updated (README, Javadoc, stored procedure comments as appropriate)
- [ ] New stored procedures have error handlers
- [ ] No `Thread.sleep()` in tests
- [ ] No new framework dependencies added without discussion
- [ ] Performance PRs include before/after benchmark numbers

---

## Performance Changes

All changes to performance-sensitive code paths must include benchmark evidence.

**Before opening the PR:**
1. Run benchmarks on the base branch, record numbers
2. Apply your change
3. Run benchmarks again, record numbers
4. Include both in the PR description

**Benchmark command:**
```bash
# Run the publish benchmark
mvn test -pl boxy-core -Dtest=BenchmarkIT

# Run the full pipeline benchmark (once created)
mvn test -pl boxy-core -Dtest=PipelineBenchmarkIT
```

**Gate criteria:**
- Performance improvements: after must be measurably better (>5% for latency, >10% for throughput)
- Correctness fixes: regression is acceptable, must be noted with justification
- Neutral changes: fine to merge

---

## Schema and SQL Changes

### Adding a new stored procedure

1. Create the SQL file: `boxy-db/src/main/resources/db/changelog/mysql/sp/sp_<entity>__<action>.sql`
2. Add a changeset to the MySQL changelog referencing the file
3. Add a Java repository method that calls the procedure
4. Add an integration test

### Modifying an existing stored procedure

1. **Never edit an existing changeset** — add a new one instead
2. Update the SQL file (the source of truth for the current version)
3. Add a new changeset that drops and recreates the procedure
4. Update Liquibase changelog
5. Update integration tests as needed

### Schema migrations

- All schema changes go through Liquibase
- Test your migration with `mvn liquibase:update` against a fresh database
- Always test rollback if Liquibase supports it for your change type

---

## Documentation

Good documentation is a first-class concern in Boxy. Every PR should leave documentation
better than it found it.

- **`README.md`** — user-facing overview; update for any user-visible change
- **`docs/`** — detailed guides (benchmarks, configuration tuning, operational runbook)
- **Javadoc** — all public classes and methods
- **Stored procedure comments** — parameters, return values, error codes
- **`CHANGELOG.md`** — every completed item from `PRODUCTION_READINESS.md`

---

## Questions?

Open a GitHub issue tagged `question`. If you're an AI agent and something is ambiguous,
leave a comment in your draft PR describing the ambiguity rather than guessing.
