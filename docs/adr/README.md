# Architecture Decision Records (ADRs)

This directory contains Architecture Decision Records for Boxy, documenting key design choices and their rationale.

## ADR Index

| ADR | Title | Status |
|-----|-------|--------|
| [001](001-mysql-stored-procedure-protocol.md) | Use MySQL Stored Procedures as Client Protocol | Accepted |
| [002](002-partition-id-encoding.md) | Partition ID Encoding: `(topic_id << 16) + partition_number` | Accepted |
| [003](003-cursor-lease-model.md) | Cursor Lease Model for Consumer Ownership | Accepted |
| [004](004-json-for-sp-arguments.md) | Use JSON for Multi-Value Stored Procedure Arguments | Accepted |
| [005](005-liquibase-versioned-sps.md) | Versioned Stored Procedures with Liquibase | Accepted |

## ADR Format

Each ADR follows this structure:

1. **Title**: Brief, past-tense description of the decision
2. **Status**: Proposed, Accepted, Deprecated, Superseded
3. **Context**: Problem statement, constraints, competing options
4. **Decision**: The chosen solution
5. **Consequences**: Benefits and drawbacks
6. **Related Decisions**: Links to related ADRs

## How to Read ADRs

Start with the context section to understand the problem. The decision section explains the chosen approach. Read consequences to understand trade-offs.

## Adding New ADRs

To propose a new ADR:

1. Create a file: `docs/adr/NNN-title-in-kebab-case.md`
2. Use the template format
3. Set status to "Proposed"
4. Update the index in this README
5. Submit for review

Example:
```
# ADR 006: Event Ordering Guarantees

## Status
Proposed

## Context
...

## Decision
...

## Consequences
...
```

---

## Key Design Principles

These principles guide all Boxy design decisions:

1. **MySQL-as-datastore**: Leverage MySQL for ordering and durability; avoid external dependencies.
2. **Protocol via stored procedures**: SPs provide atomic operations, error signaling, and decoupling.
3. **Transient event model**: Events are cleaned up after retention; system is optimized for consumption, not archival.
4. **Lease-based ownership**: Explicit leases replace implicit consumer groups; simpler failure detection.
5. **Configurability at runtime**: All operational tuning parameters are in `boxy_config` table; no restart required.

---

## Related Documentation

- [PRODUCTION_READINESS.md](../../PRODUCTION_READINESS.md) — Comprehensive readiness checklist
- [README.md](../../README.md) — Project overview
- [docs/](../README.md) — User-facing documentation
