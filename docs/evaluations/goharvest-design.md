# Evaluation: GoHarvest Design Pattern for Boxy

**Item 128/165** — Evaluate whether the GoHarvest pattern (batch-oriented message processing) could improve throughput or reduce latency in Boxy's event polling.

## Background

GoHarvest is an internal pattern used at some high-throughput systems to optimize batch processing of messages. The core idea:
1. Collect messages in a harvester goroutine until batch size or timeout
2. Pass batches to workers in bulk
3. Workers process and acknowledge in parallel
4. Reduce context switches and database round-trips

## Current State

Boxy's polling model:
```
Consumer Group (e.g., 4 partitions)
  ├─ Worker 1 (polls partition 0)
  │  └─ sp_events__poll() every 5s -> Process -> sp_cursors__commit()
  ├─ Worker 2 (polls partition 1)
  │  └─ sp_events__poll() every 5s -> Process -> sp_cursors__commit()
  ├─ Worker 3 (polls partition 2)
  │  └─ sp_events__poll() every 5s -> Process -> sp_cursors__commit()
  └─ Worker 4 (polls partition 3)
     └─ sp_events__poll() every 5s -> Process -> sp_cursors__commit()
```

**Characteristics**:
- Independent per-partition polling (4 separate sp_events__poll calls)
- Sequential processing per partition
- Cursor commits after each batch
- Latency: ~100-200ms per event (poll timeout + processing)

## Options Evaluated

### Option A: Continue with Current Per-Partition Model

**Pros**:
- Simple to understand and maintain
- Fine-grained cursor tracking (per partition)
- Works well for moderate throughput (<100K events/sec)
- Current implementation is solid

**Cons**:
- High database round-trips (one poll per partition)
- Potential contention if many consumer groups poll simultaneously
- Latency: ~100-200ms per event due to poll timeout
- Not optimized for bursty workloads

**Throughput**: ~50-100K events/sec (database I/O bound)

### Option B: GoHarvest Pattern (Centralized Harvester)

Implement a centralized "Harvester" that polls from all partitions in a single trip, collects events, and distributes to workers:

```
Consumer Group (e.g., 4 partitions)
  └─ Harvester (main coordinator)
     ├─ Poll all partitions (one sp_events__poll call, OR manual per-partition queries)
     ├─ Collect into shared queue (max 1000 events)
     ├─ Distribute to Workers (batch of 250 each)
     └─ Coordinate cursor commits (after all workers ACK)
        ├─ Worker 1 -> Process -> ACK
        ├─ Worker 2 -> Process -> ACK
        ├─ Worker 3 -> Process -> ACK
        └─ Worker 4 -> Process -> ACK
```

**Pros**:
- Fewer database round-trips (1 harvester query vs 4 worker queries)
- Reduced contention (single database thread per consumer group)
- Batching reduces I/O overhead
- Better for bursty workloads
- Potential for 2-3x throughput improvement

**Cons**:
- More complex (harvester coordination, queue management)
- Harvester becomes a bottleneck if it blocks
- Cursor commit latency increases (must wait for all workers)
- Harder to debug and reason about
- Requires careful backpressure handling (queue overflow)

**Throughput estimate**: ~150-300K events/sec (2-3x improvement)

**Implementation complexity**: Medium (1-2 weeks)

### Option C: Hybrid: Adaptive Batching

Implement adaptive batching that:
- Polls in small batches (10-50 events per partition)
- Holds events in memory for up to 100ms
- Distributes larger batches to workers when available
- Falls back to per-partition processing if latency exceeds threshold

**Pros**:
- Better throughput than Option A (50-100% improvement)
- Maintains low latency in low-traffic scenarios
- Reduces database load in high-traffic scenarios
- More backward-compatible

**Cons**:
- Moderate complexity
- Requires tuning for optimal batch size and timeout
- State management (buffering, timeouts)

**Throughput estimate**: ~100-150K events/sec

**Implementation complexity**: Low-Medium (1 week)

## Recommendation

**Option C: Hybrid Adaptive Batching** (defer Option B to later versions)

### Rationale

1. **Risk/Reward**: Option C provides 50-100% throughput improvement with moderate complexity.
2. **Latency**: Maintains <50ms latency in low-traffic, reduces to polling timeout (5s) in high-traffic.
3. **Deployment**: Can be rolled out as an opt-in feature (e.g., `boxy.harvester.enabled=true`).
4. **Fallback**: If issues arise, can disable harvesting without rewriting.
5. **Path to 1M events/sec**: Provides foundation for Option B later.

### Implementation Plan

1. **Create Harvester class** in `boxy-core/worker/`:
   ```java
   public class AdaptiveHarvester<T> {
       private final EventBatch buffer;
       private final ScheduledExecutorService scheduler;
       private final Duration batchTimeout = Duration.ofMillis(100);
       
       public void poll() {
           // Poll all partitions
           // Buffer events until size threshold or timeout
           // Notify workers when batch ready
       }
   }
   ```

2. **Update Worker** to accept batches from harvester:
   ```java
   public class Worker<T> {
       private final Optional<AdaptiveHarvester<T>> harvester;
       
       private void pollLoop() {
           if (harvester.isPresent()) {
               List<PolledEvent<T>> events = harvester.get().nextBatch();
           } else {
               List<PolledEvent<T>> events = pollEvents();  // Current behavior
           }
       }
   }
   ```

3. **Add configuration**:
   ```properties
   boxy.harvester.enabled=true
   boxy.harvester.batchSizeThreshold=100
   boxy.harvester.batchTimeoutMs=100
   ```

4. **Benchmark** improvements:
   ```bash
   mvn clean verify -Pbench-cloud-small \
     -Dboxy.harvester.enabled=true
   ```

5. **Monitor** harvester queue depth and latency:
   ```
   boxy.harvester.queue.depth       # Events buffered
   boxy.harvester.batch.latencyMs   # Time from poll to worker
   ```

### Future: Full GoHarvest (Option B)

If Boxy needs to exceed 300K events/sec:
1. Centralize all polling into a single database thread per consumer group
2. Implement partition-aware batching (group events by partition)
3. Commit cursors only after all workers ACK their partitions

Estimated gain: 2-3x (up to 1M events/sec).

## References

- [Apache Pulsar Consumer Batching](https://pulsar.apache.org/docs/concepts-messaging/#batching)
- [Kafka Consumer Batching](https://kafka.apache.org/documentation/#consumerconfigs_fetch.max.bytes)
- [Backpressure Patterns in Java](https://www.baeldung.com/java-backpressure)

## Metrics

- **Harvester queue depth**: Should stay <50% full
- **Batch latency**: Should be <150ms (100ms timeout + processing)
- **Worker throughput**: Should increase 1.5-2x

## Timeline

- **Milestone**: Version 1.1 (post-release)
- **Effort**: Low-Medium (1 week for adaptive batching)
- **Risk**: Low (opt-in feature, fallback available)
