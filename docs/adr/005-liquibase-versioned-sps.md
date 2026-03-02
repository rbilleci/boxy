# ADR 005: Versioned Stored Procedures with Liquibase

## Status
Accepted

## Context

Stored procedures need to evolve (configuration parameters, bug fixes). System must:
1. Support multiple versions coexisting (v1, v2, v3)
2. Avoid breaking changes: old clients continue working
3. Deploy safely: idempotent changesets

**Options**:
1. **In-place updates**: Overwrite definitions (breaks old clients)
2. **Versioned procedures**: `sp_events__poll_v1`, `sp_events__poll_v2`, etc.
3. **Polymorphic dispatch**: Single name; internal routing
4. **Liquibase with checksums**: Strict version control

**Constraints**:
- MySQL 8.0+ supports DROP PROCEDURE IF EXISTS
- Liquibase already used for schema
- Procedures have side effects

## Decision

Use **versioned procedures with Liquibase changesets**:

1. **Naming**: `sp_events__poll_v1`, `sp_events__poll_v2`, etc.
2. **Changeset per version**: Unique ID and checksum
3. **Gradual migration**: Clients upgrade at their pace
4. **Latest is default**: New clients call latest; old versions remain

**File structure**:
```
boxy-db/src/main/resources/db/changelog/mysql/sp/
  sp_events__poll_v1.sql
  sp_events__poll_v2.sql
  sp_events__poll_v3.sql
  sp_events__poll_v4.sql
```

## Consequences

### Positive

1. **Non-breaking**: Old clients continue working
2. **Rollback-safe**: Old versions remain deployed
3. **Audit trail**: Changelog documents all versions
4. **Gradual migration**: Independent client upgrades
5. **Easy comparison**: Git diff shows changes

### Negative

1. **Proliferation**: Many versions accumulate
2. **Maintenance overhead**: Fixes needed in multiple versions
3. **Testing burden**: All versions tested
4. **Deployed bloat**: Schema grows over time

## Migration Strategy

After 12-month deprecation:
1. Announce EOL
2. Create cleanup changeset dropping old versions
3. Update docs

## Related Decisions

- [ADR 001](001-mysql-stored-procedure-protocol.md) — SPs as protocol
- [ADR 004](004-json-for-sp-arguments.md) — JSON handling added in v2
