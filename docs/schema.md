# Schema Design Notes & Evaluations

This document records schema design decisions and evaluations for the Boxy event streaming database.

## Event Retention (Items #95-#97)

The `events.created_at` column (added in changeset 12) enables time-based retention queries.
Use `sp_events__cleanup(retain_days, batch_size)` for batch deletion:

```sql
-- Delete events older than 30 days, 1000 rows per batch
CALL sp_events__cleanup(30, 1000);

-- Schedule automated cleanup (optional):
CREATE EVENT events_cleanup
    ON SCHEDULE EVERY 1 HOUR
    DO CALL sp_events__cleanup(30, 1000);
```

**Note**: The cleanup procedure only deletes from the `events` table. The `sequences` table
retains sequence numbers (for audit/replay). If sequence rows are also to be cleaned,
a separate cleanup or cascade delete is needed.

## Evaluation: Event Type / Content Type Column (Item #101)

**Decision: Deferred.**

Adding an `event_type` or `content_type` column to `events` would add ~8-16 bytes per row and
require schema migration + index updates. The current `data` column stores arbitrary payloads;
callers can embed type information within the JSON payload. This avoids schema lock-in for
evolving event schemas.

**Gate**: Re-evaluate if a significant fraction of consumers consistently filter by event type,
causing unnecessary deserialization of non-matching payloads.

## Evaluation: Events Table Partitioning (Item #102)

**Decision: Deferred.**

MySQL range partitioning on `created_at` or key-based partitioning would enable fast partition
pruning for cleanup and could improve write throughput by distributing INSERT locks across
partitions. However:
- Partitioning adds operational complexity (partition management, DDL locks)
- MySQL's `AUTO_INCREMENT` primary key conflicts with partitioning requirements
- At < 1 billion rows, a well-indexed InnoDB table with `idx_events__created_at` and
  batch-delete cleanup is adequate

**Gate**: Evaluate when the events table exceeds 500M rows or cleanup operations exceed 5 minutes.

## Evaluation: Data Model Normalization (Item #103)

**Decision: Current model is appropriate.**

The `consumers.topic_ids` JSON column denormalizes topic IDs into a single row for O(1) lookup
during polling. Normalizing this into a join table would:
- Require a JOIN on every poll call (additional I/O)
- Complicate the `JSON_CONTAINS` check in sp_events__poll

The denormalized design is intentional and aligns with the poll hot-path performance requirements.

## Evaluation: Partial Indexes (Item #104)

**Decision: Evaluate post-benchmark.**

Candidate partial indexes:
1. `consumer_leases WHERE locked_until IS NOT NULL` — filters to active leases only
2. `cursors WHERE position < (SELECT MAX(high_watermark) FROM partitions p WHERE p.id = partition_id)` — 
   skips idle cursors; requires computed expression or function-based index (MySQL 8.0+)

**Gate**: Implement if poll p99 latency with 10,000+ idle cursors degrades by > 20% vs. baseline.
Use `EXPLAIN` output with `Using index condition` as the success criterion.
