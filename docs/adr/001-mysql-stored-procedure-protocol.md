# ADR 001: Use MySQL Stored Procedures as Client Protocol

## Status
Accepted

## Context

Boxy needs a protocol for clients (producers and consumers) to interact with a MySQL-backed event system. Options:

1. **Stored procedures**: SPs as RPCs; clients call `sp_events__publish()`, etc.
2. **ORM/JDBC layer**: Applications issue raw SQL; library provides mapping objects.
3. **HTTP API**: REST endpoint wrapping database operations.
4. **gRPC**: Protocol buffer service for cross-language clients.

**Constraints**:
- Must support atomic multi-table operations (e.g., insert event + queue for sequencing).
- Must provide error signaling for business errors (UNKNOWN_TOPIC, PAYLOAD_TOO_LARGE).
- Must work with MySQL 8.0+ for event scheduler support.
- Minimize latency and per-operation overhead.

## Decision

Use MySQL stored procedures as the primary client protocol.

- **Publish**: `CALL sp_events__publish(path, topic, key, data)`
- **Poll**: `CALL sp_events__poll(consumer_id, batch_size)` → 2 result sets
- **Commit**: `CALL sp_cursors__commit(consumer_id, cursor_positions)`

Clients are thin JDBC wrappers (Java repositories) that:
1. Call stored procedures
2. Map result sets to domain records
3. Handle retry logic and error classification

## Consequences

### Positive

1. **Atomic operations**: SPs guarantee all-or-nothing semantics within a transaction.
2. **Business error signaling**: SIGNAL SQLSTATE allows error classification.
3. **Database-side logic**: Complex operations are server-side; clients are simple.
4. **Language-agnostic**: Any language with JDBC/MySQL driver can use Boxy.
5. **Observability**: SQL logging and profiling tools work directly.

### Negative

1. **Stored procedure overhead**: SP execution adds ~1-2ms per call vs. raw SQL.
2. **Limited tooling**: SPs are harder to debug than HTTP endpoints.
3. **SQL code**: SPs require SQL expertise.
4. **Language lock-in**: Boxy is tied to MySQL.

## Related Decisions

- [ADR 002](002-partition-id-encoding.md) — Partition ID encoding
- [ADR 004](004-json-for-sp-arguments.md) — JSON arguments
- [ADR 005](005-liquibase-versioned-sps.md) — Versioned SPs
