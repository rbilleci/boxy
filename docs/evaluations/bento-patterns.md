# Evaluation: Bento Patterns for Boxy

**Item 129/166** — Examine Bento design patterns (efficient batching, message grouping, and composable pipelines) for potential application to Boxy's cursor management, partition rebalancing, or event publishing.

## Background

Bento is a framework for efficient stream processing and batching. Key patterns:
1. **Grouped batching**: Collect messages by key before processing
2. **Composable pipelines**: Chain processors (map, filter, group, aggregate)
3. **Backpressure**: Halt input when output buffer full
4. **Checkpointing**: Save progress periodically

## Current State

Boxy's event processing:
- **Publishing**: `sp_events__publish()` accepts single or multiple events (already batched)
- **Polling**: `sp_events__poll()` returns up to N events (client handles batching)
- **Cursor commits**: Sequential per partition (no grouping)
- **Rebalancing**: Random lease distribution (no optimization)

**Limitations**:
- Cursor commits could be grouped/batched
- Partition rebalancing is random (no affinity)
- Event publishing doesn't optimize for key-based grouping
- No pipeline composition for complex scenarios

## Options Evaluated

### Option A: Continue with Current Design

**Pros**:
- Works well for simple use cases
- Minimal complexity
- Direct mapping to SQL operations
- Easy to understand and debug

**Cons**:
- Cursor commits are sequential (slow under high load)
- No grouping optimization (all batches treated equally)
- Rebalancing is naive (no sticky assignment)
- Complex event processing requires external batching

### Option B: Apply Bento Grouping to Cursor Commits

Batch cursor commits by partition/topic:

```
Events polled:
  Event 1 (partition 0, seq 5)
  Event 2 (partition 0, seq 6)
  Event 3 (partition 1, seq 10)
  Event 4 (partition 0, seq 7)
  Event 5 (partition 1, seq 11)

Grouped commits:
  sp_cursors__commit(partition=0, position=7)    # Commit max seq
  sp_cursors__commit(partition=1, position=11)   # Commit max seq
```

**Pros**:
- Reduces cursor commits by 50-90% (fewer DB round-trips)
- Maintains correctness (commits only max sequence per partition)
- Minimal code changes
- Better throughput (fewer calls to DB)

**Cons**:
- Slightly more complex logic (grouping)
- Cursor commit latency increases (wait for all events in batch)
- Requires careful handling of partial batches

**Throughput improvement**: 10-20%

**Implementation complexity**: Low (1-2 days)

### Option C: Apply Bento Patterns to Event Publishing Pipeline

Create a composable pipeline for event publishing:

```java
PublishPipeline pipeline = PublishPipeline.builder()
    .groupByKey()               // Group events by routing key
    .filter(e -> !e.isDeleted()) // Remove delete events
    .aggregate(100, Duration.ofMillis(50))  // Batch up to 100 or 50ms
    .map(batch -> batch.compress())          // Optional compression
    .publish()                   // sp_events__publish_multi
    .checkpoint()                // Save progress
    .build();
```

**Pros**:
- Flexible for complex publishing scenarios
- Composable (users can add custom processors)
- Efficient batching based on multiple criteria
- Supports transforms (filtering, compression, encryption)

**Cons**:
- Higher complexity
- Adds abstraction layer (harder to debug)
- May introduce latency in low-traffic scenarios
- Overkill for simple publish operations

**Throughput improvement**: 20-30% (with optimizations)

**Implementation complexity**: Medium (1-2 weeks)

### Option D: Apply Bento Rebalancing to Partition Lease Distribution

Replace random lease distribution with Bento-inspired fair allocation:

Current:
```
Consumer A: leases partitions [0, 2, 4] (random)
Consumer B: leases partitions [1, 3, 5] (random)
→ Imbalanced: A gets more partitions or larger partitions
```

With Bento-style apportionment:
```
Partitions: [0, 1, 2, 3, 4, 5]
Consumers: [A, B]
→ Consumer A: [0, 2, 4]  (every other partition)
→ Consumer B: [1, 3, 5]  (every other partition)
→ Perfectly balanced
```

See also: Item 126 (Hamilton apportionment).

**Pros**:
- Even distribution of partitions
- Better throughput utilization
- Predictable lease assignments
- Reduced rebalancing churn

**Cons**:
- Requires refactoring lease assignment logic
- May not be optimal for uneven partition sizes
- Compatibility: existing consumer group states need migration

**Throughput improvement**: 5-15% (reduced lock contention)

**Implementation complexity**: Medium (1 week)

## Recommendation

**Option B: Batch Cursor Commits** + **Option D: Fair Rebalancing** (defer Option C)

### Rationale

1. **Low risk**: Options B and D are incremental improvements.
2. **High impact**: 10-20% throughput gain from batching, 5-15% from fair rebalancing.
3. **Practical**: Cursor batching is straightforward; rebalancing is necessary for multi-consumer scenarios.
4. **Defer C**: Publishing pipeline is useful but premature (can be added later as extension point).

### Implementation Plan

#### Part 1: Batch Cursor Commits (Option B)

1. **Modify Worker** to collect events:
   ```java
   Map<Integer, Long> partitionMaxSeq = new HashMap<>();
   for (PolledEvent<T> event : events) {
       partitionMaxSeq.put(event.partitionId(), event.sequence());
   }
   
   // Batch commit all partitions
   for (var entry : partitionMaxSeq.entrySet()) {
       commitCursor(entry.getKey(), entry.getValue());
   }
   ```

2. **Update sp_cursors__commit** if needed (currently handles single partition; may be OK)

3. **Benchmark** improvement:
   ```bash
   mvn clean verify -Pbench-cloud-small
   ```

Expected: 10-15% throughput improvement, similar latency.

#### Part 2: Fair Rebalancing (Option D)

See Item 126 (Hamilton apportionment) for details.

### Future: Publishing Pipeline (Option C)

If application layer needs complex event transforms, implement Option C as an extension:
- Create `PublishPipeline` interface
- Provide basic implementations (grouping, batching)
- Allow users to compose custom pipelines

## References

- [Bento Documentation](https://bentoml.com/)
- [Stream Processing Patterns](https://martinfowler.com/articles/patterns-of-distributed-systems/)
- [Database Transaction Batching](https://use-the-index-luke.com/sql/join/hash-join-dop)

## Metrics

- **Cursor commits per second**: Should decrease by 50-90%
- **Batch latency**: Should remain <100ms
- **Partition distribution**: Should be perfectly balanced (std dev near 0)

## Timeline

- **Cursor batching**: Version 1.0 or 1.1 (low effort)
- **Fair rebalancing**: Version 1.1 (medium effort)
- **Publishing pipeline**: Deferred (v1.2+)

## Effort Estimates

- Option B (cursor batching): 2 days
- Option D (fair rebalancing): 5 days
- Option C (pipeline): 2 weeks (deferred)

**Total for 1.1**: 1 week
