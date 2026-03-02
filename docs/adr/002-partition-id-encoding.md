# ADR 002: Partition ID Encoding: `(topic_id << 16) + partition_number`

## Status
Accepted

## Context

Boxy partitions events by topic and partition number. The system needs:
1. Unique partition IDs
2. Efficient lookups
3. Support for ~65K topics and ~65K partitions per topic

**Options**:
1. **Composite key**: (topic_id, partition_number)
2. **Hash encoding**: MD5(CONCAT(topic_id, partition_number))
3. **Bit-shift encoding**: (topic_id << 16) + partition_number
4. **Sequential auto-increment**: Global auto_increment

## Decision

Use bit-shift encoding: `partition_id = (topic_id << 16) + partition_number`

**Formula**:
```sql
SET partition_id = (topic_id << 16) + partition_number;
```

## Consequences

### Positive

1. **Unique, compact IDs**: Every pair maps to unique BIGINT.
2. **No join needed**: Direct insert without lookup.
3. **Efficient**: Bit operations are O(1).
4. **Reversible**: Can decode back to topic_id and partition_number.
5. **Deterministic**: Same inputs always produce same ID.

### Negative

1. **Encoding coupling**: Changes break existing deployments.
2. **Brittle limits**: Max partitions per topic ~65K.
3. **Not obvious**: Unfamiliar developers find encoding opaque.

## Related Decisions

- [ADR 001](001-mysql-stored-procedure-protocol.md) — Encoding in `sp_events__publish()`
