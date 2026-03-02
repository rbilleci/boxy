# Core Enhancements Roadmap (Section 15, Items 123-130)

This document outlines planned enhancements to Boxy's core functionality. These items build on the foundation established in Sections 1-11 and address scalability, flexibility, and operational concerns.

## Item 123/160: Partition Resizing

**Status**: Planned
**Complexity**: Medium
**Effort**: 5 days

Allow topics to add or remove partitions dynamically without downtime.

### Design

1. **Add Partitions** (only supported direction initially):
   ```sql
   -- sp_topics__resize_partitions(topic_id, new_partition_count)
   -- Adds new partitions while preserving existing ones
   -- New events route to new partitions based on key hash
   ```

2. **Cursor Migration** (critical):
   - Existing cursors remain valid (point to old partitions)
   - New consumer subscriptions get new partition count
   - Gradual migration: old cursors age out as consumers rebalance

3. **Liquibase Changeset**:
   - Changeset 14: sp_topics__resize_partitions procedure
   - Atomic operation: update partition count + create partition records

### Example

```sql
-- Resize topic from 4 to 8 partitions
CALL sp_topics__resize_partitions(1, 8);

-- New events go to partitions 0-7
-- Existing cursors still track 0-3
-- Consumer rebalance will distribute new partitions to consumers
```

## Item 124/161: Topics Cache Invalidation Review

**Status**: Planned
**Complexity**: Low
**Effort**: 2 days

Review and improve topics_cache invalidation strategy.

### Current Issues

- Topics cache is write-through (sp_topics__cache_get inserts on miss)
- Cache grows unbounded (no TTL, no eviction policy)
- Potential stale data if topic metadata changes

### Solution

Create `sp_topics__cache_invalidate` procedure:

```sql
-- sp_topics__cache_invalidate(namespace_id, topic_name)
-- Removes topic from cache (forces reload on next access)
DELETE FROM topics_cache 
WHERE topic_id = p_topic_id AND topic = p_topic;
```

Call this after `sp_topics__update`:
- Partition count change
- Topic deletion (mark as deleted)
- Metadata update (description, config, etc.)

### Improvement

Add optional TTL-based eviction:
```sql
-- Expire cache entries older than 24 hours
DELETE FROM topics_cache WHERE created_at < NOW() - INTERVAL 24 HOUR;
```

## Item 125/162: Subscribe from Arbitrary Position

**Status**: Planned
**Complexity**: Low
**Effort**: 2 days

Extend subscription to support starting from arbitrary sequence positions or high watermark.

### Design

Create `sp_subscriptions__subscribe_v4`:

```sql
-- sp_subscriptions__subscribe_v4(
--   subscription, path, topic,
--   start_position  -- -1 = high watermark, 0 = beginning, N > 0 = specific position
-- )
```

Examples:
```sql
-- Subscribe from beginning (current behavior)
CALL sp_subscriptions__subscribe_v4('my-group', '/', 'events', 0);

-- Subscribe from high watermark (skip existing)
CALL sp_subscriptions__subscribe_v4('my-group', '/', 'events', -1);

-- Subscribe from specific position
CALL sp_subscriptions__subscribe_v4('my-group', '/', 'events', 1000);
```

### Implementation

Set initial cursor position based on start_position:
- 0: position = 0
- -1: position = (SELECT MAX(sequence) FROM events WHERE topic_id = ?)
- N: position = N

## Item 126/163: Fair Partition Lease Distribution (Hamilton Apportionment)

**Status**: Planned
**Complexity**: Medium
**Effort**: 5 days

Replace random partition lease assignment with fair, deterministic apportionment.

### Current Issue

Random distribution causes imbalance:
```
3 consumers, 10 partitions:
Consumer A: [0, 3, 7]       (3 partitions) ✓
Consumer B: [1, 4, 5, 8, 9] (5 partitions) ✗ unbalanced
Consumer C: [2, 6]          (2 partitions) ✗
```

### Solution: Hamilton Apportionment

Distribute partitions fairly using Hamilton's method:

```sql
-- sp_consumers__distribute_partitions(topic_id)
-- Assign partitions to consumers fairly (every consumer gets floor(p/c) or ceil(p/c))
--
-- Example: 10 partitions, 3 consumers
--   10 / 3 = 3.33
--   Base: each gets 3 (9 total)
--   Remainder: 1 partition
--   Final: [4, 3, 3] distribution

-- Assignment is deterministic and reproducible
```

### Benefits

- **Balanced**: std deviation near 0
- **Deterministic**: same distribution after rebalance
- **Minimal churn**: consumers keep most of their partitions

## Items 127-130: Evaluation Documents

**Status**: Complete
**Location**: `docs/evaluations/`

See individual files:
- `async-jdbc.md` (Item 127) — Virtual threads recommended
- `goharvest-design.md` (Item 128) — Adaptive batching recommended
- `bento-patterns.md` (Item 129) — Cursor batching + fair rebalancing recommended
- `mysql-jdbc-tuning.md` (Item 130) — Configuration guide for 1M evt/s target

## Implementation Timeline

### Version 1.0 (Current)
- All sections 1-13 complete
- Worker API complete
- SDK scaffolds complete

### Version 1.1 (Q2 2026)
- **Item 126**: Fair partition distribution
- **Item 124**: Cache invalidation review
- **Item 125**: Subscribe from arbitrary position
- Adaptive batching (from Item 128 evaluation)

### Version 1.2 (Q3 2026)
- **Item 123**: Partition resizing
- Virtual thread optimization (from Item 127 evaluation)
- SDK implementations (Go, Python, Rust, .NET, JavaScript)

### Version 1.3+ (Future)
- Full GoHarvest pattern (centralized harvester)
- Publishing pipeline (composable transforms)
- Advanced lease rebalancing strategies

## Success Metrics

| Item | Metric | Target | Status |
|------|--------|--------|--------|
| 123 | Resizing uptime | Zero downtime | Planned |
| 124 | Cache size | < 1MB for 100K topics | Planned |
| 125 | API coverage | All start positions | Planned |
| 126 | Distribution balance | std dev < 1 | Planned |
| 127 | Throughput (virtual threads) | 1M evt/sec | Evaluation |
| 128 | Throughput (adaptive batch) | 150K evt/sec | Evaluation |
| 129 | Commit latency | < 100ms | Evaluation |
| 130 | Tuning guide readiness | Complete | Evaluation |

## Dependencies

- Item 123 depends on robust cursor migration logic
- Items 124-125 are independent
- Item 126 requires fair hashing of partition assignments
- Items 127-130 are evaluations (no hard dependencies)

## See Also

- [Release Process](release-process.md)
- [Docker Setup](docker.md)
- [Architecture](architecture.md)
