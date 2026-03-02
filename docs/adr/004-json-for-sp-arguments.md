# ADR 004: Use JSON for Multi-Value Stored Procedure Arguments

## Status
Accepted

## Context

Some procedures accept variable-length input:
- `sp_events__publish_multi()`: 1-1000 events
- `sp_cursors__commit()`: 1-1000 cursor position updates

**Options**:
1. **Multiple calls**: Loop in application (N calls for N items)
2. **Comma-separated strings**: Parse delimited strings
3. **JSON arrays/objects**: Use MySQL JSON functions
4. **Temp table**: Client creates and fills temp table
5. **Stored procedure with loop**: Array parameter (not portable)

**Constraints**:
- MySQL 8.0+ with JSON support
- Must be atomic (all-or-nothing)
- Error handling

## Decision

Use **JSON for multi-value arguments**:

**Publish batch** (JSON array):
```sql
CALL sp_events__publish_multi(
    '[{"path": "...", "topic": "...", "key": "...", "data": "..."}]'
)
```

**Commit positions** (JSON object):
```sql
CALL sp_cursors__commit(
    'consumer-1',
    '{"123": 456, "124": 789}'
)
```

## Consequences

### Positive

1. **Type-safe**: JSON schema can be validated
2. **Extensible**: Adding fields doesn't change signature
3. **Efficient**: JSON_TABLE joins directly
4. **Atomic**: Entire batch committed/rolled back
5. **Language-agnostic**: All languages construct JSON
6. **Self-documenting**: Clear structure

### Negative

1. **Serialization overhead**: ~1-2ms per call
2. **Error messages**: Less helpful than type errors
3. **Debugging**: Harder to trace in logs
4. **Schema validation**: Manual in SP

## Related Decisions

- [ADR 001](001-mysql-stored-procedure-protocol.md) — JSON parsing in SPs
- [ADR 005](005-liquibase-versioned-sps.md) — JSON handling evolved
