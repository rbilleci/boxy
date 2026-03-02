# Stored Procedures Reference

This document provides comprehensive documentation for all Boxy stored procedures, including parameters, behavior, error codes, and side effects.

## Table of Contents

- [Event Publishing](#event-publishing)
- [Event Polling](#event-polling)
- [Event Cleanup](#event-cleanup)
- [Sequencing](#sequencing)
- [Consumer Management](#consumer-management)
- [Cursor Management](#cursor-management)
- [Subscription Management](#subscription-management)
- [Topic Management](#topic-management)
- [Configuration Management](#configuration-management)

---

## Event Publishing

### sp_events__publish_v3

**Purpose**: Publish a single event atomically.

**Signature**:
```sql
CALL sp_events__publish(
    IN p_path  VARCHAR(4000),   -- Namespace path (e.g., 'tenant-a/payments')
    IN p_topic VARCHAR(500),    -- Topic name (e.g., 'orders')
    IN p_key   VARCHAR(255),    -- Routing key for CRC32 partition selection
    IN p_data  LONGBLOB         -- Event payload (typically JSON)
);
```

**Parameters**:
- `p_path`: Fully-qualified namespace path; must exist in the `namespaces` table
- `p_topic`: Topic name; must exist under the given namespace
- `p_key`: Partition key; CRC32(key) % partitions determines the target partition
- `p_data`: Raw event payload; size validated against `boxy_config: max.event.payload.bytes`

**Behavior**:
1. Validates payload size: if `LENGTH(p_data) > max_payload_bytes`, raises SIGNAL error `PAYLOAD_TOO_LARGE`
2. Resolves namespace path to `namespace_id` via MD5 hash lookup (`namespaces.path_hash`)
3. Resolves topic name to `topic_id`
4. Computes partition: `partition_number = CRC32(p_key) % topic.partitions`
5. Resolves partition to `partition_id` using `fn_resolve_partition_id()`
6. Atomically inserts into `events` and `unprocessed_events` within a transaction
7. Returns without result set

**Error Codes**:
- `PAYLOAD_TOO_LARGE`: Event payload exceeds configured maximum
- `UNKNOWN_TOPIC`: Topic not found under the given namespace
- `UNKNOWN_NAMESPACE`: Namespace path does not exist
- `PARTITION_NOT_FOUND`: Unable to resolve partition (internal error)

**Side Effects**:
- Creates a new row in `events` table with `created_at = NOW(3)`
- Creates a corresponding row in `unprocessed_events` for sequencer pickup
- Increments auto-increment counter (affects subsequent `LAST_INSERT_ID()`)

**Configuration**:
- `boxy_config: max.event.payload.bytes` (default 1048576 = 1 MiB)

---

### sp_events__publish_multi_v4

**Purpose**: Publish multiple events atomically in a batch.

**Signature**:
```sql
CALL sp_events__publish_multi(
    IN p_events_json JSON   -- Array of {path, topic, key, data} objects
);
```

**Parameters**:
- `p_events_json`: JSON array with elements of the form:
  ```json
  {
    "path": "tenant-a/payments",
    "topic": "orders",
    "key": "order-12345",
    "data": "{\"order_id\": 12345, \"amount\": 99.99}"
  }
  ```

**Behavior**:
1. Validates array size: if `JSON_LENGTH(p_events_json) > max_batch_size`, raises SIGNAL error `BATCH_TOO_LARGE`
2. Expands JSON array using `JSON_TABLE()` into a temporary table
3. Joins with `namespaces` and `topics` to resolve partitions
4. Inserts all events into the `events` table in a single transaction (contiguous auto-increment)
5. Inserts corresponding rows into `unprocessed_events`
6. Returns without result set

**Error Codes**:
- `BATCH_TOO_LARGE`: Array contains more events than configured maximum
- `UNKNOWN_TOPIC`: One or more topics not found
- `UNKNOWN_NAMESPACE`: One or more namespaces do not exist
- `MALFORMED_JSON`: `p_events_json` is not a valid JSON array

**Side Effects**:
- Creates multiple rows in `events` and `unprocessed_events` tables
- All inserts are atomic: either all succeed or the entire call rolls back
- Uses a temporary table `_pub_batch` (auto-cleaned on transaction end)

**Configuration**:
- `boxy_config: max.batch.size` (default 1000)

**Performance Notes**:
- Contiguous auto-increment across all events in the batch
- Single transaction reduces overhead compared to N individual `sp_events__publish` calls
- Temporary table is memory-resident (ENGINE=InnoDB) for speed

---

## Event Polling

### sp_events__poll_v4

**Purpose**: Poll events for a consumer with adaptive lease management.

**Signature**:
```sql
CALL sp_events__poll(
    IN p_consumer_id VARCHAR(36),   -- Consumer identifier (e.g., 'consumer-1')
    IN p_batch_size  INT            -- Max events to return (0 = use default from config)
);
-- Result set 1: Events
-- Result set 2: Metadata (polling_probability)
```

**Parameters**:
- `p_consumer_id`: Must be registered in the `consumers` table; if unknown, raises SIGNAL `UNKNOWN_CONSUMER`
- `p_batch_size`: Clamped to [1, 10000]; 0 or NULL defaults to configured batch size

**Behavior**:
1. **Consumer lookup**: Retrieves `subscription_id` and `topic_ids` for the consumer
2. **Heartbeat update**: Updates `consumers.heartbeat_detected_at` and `heartbeat_deadline`
3. **Active consumer refresh**: Counts active (non-expired) consumers per topic
4. **Polling probability**: Computes adaptive probability based on active consumer count and heartbeat interval
5. **Candidate selection**: Finds unread events for consumer's cursor leases
6. **Lease acquisition**: Acquires time-limited leases on selected cursors (duration from `boxy_config: lease.lock.seconds`)
7. **Position advance**: Updates `consumer_leases.last_read_position` to track read cursor
8. **Event return**: Returns selected events with cursor IDs and payloads
9. **Metadata return**: Returns computed `polling_probability` for caller use

**Result Sets**:
- **Result 1** (events):
  ```
  cursor_id (BIGINT), partition_id (BIGINT), sequence (BIGINT), event_id (BIGINT), data (LONGBLOB)
  ```
- **Result 2** (metadata):
  ```
  polling_probability (DOUBLE)
  ```

**Error Codes**:
- `UNKNOWN_CONSUMER`: Consumer ID not found in the `consumers` table

**Side Effects**:
- Updates `heartbeat_detected_at` and `heartbeat_deadline` on the consumer
- Updates `active_consumers` count in `subscription_topics`
- Creates or updates rows in `consumer_leases` for acquired partitions
- Advances `last_read_position` in `consumer_leases` for read sequences

**Configuration**:
- `boxy_config: lease.lock.seconds` (default 3)
- `boxy_config: poll.batch.size` (default 100)

**Adaptive Behavior**:
- `polling_probability = 1.0 / (heartbeat_interval * max(active_consumers, 1))`
- Higher probability with fewer consumers; lower probability with many active consumers
- Helps distribute load fairly across consumer instances

---

## Event Cleanup

### sp_events__cleanup

**Purpose**: Delete old events that have passed the retention window.

**Signature**:
```sql
CALL sp_events__cleanup(
    IN p_cutoff_time DATETIME(3),   -- Delete events with created_at < this time
    IN p_batch_size  INT            -- Max events to delete per call (0 = default 10000)
);
```

**Parameters**:
- `p_cutoff_time`: Timestamp threshold; events older than this are deleted
- `p_batch_size`: Batch size for DELETE operation; 0 defaults to 10000

**Behavior**:
1. Finds up to `p_batch_size` rows in `events` where `created_at < p_cutoff_time`
2. Deletes matching rows in a single transaction
3. Returns number of rows deleted (via `ROW_COUNT()`)

**Side Effects**:
- Permanently deletes rows from `events` table
- Does NOT modify `sequences` table (preserves sequence numbering)
- Does NOT delete from `unprocessed_events` (cleanup is separate)

**Retention Strategy**:
- Call repeatedly with the same `p_cutoff_time` and `p_batch_size` to process large backlog incrementally
- Recommended: schedule daily or weekly based on retention policy

**Example**:
```sql
-- Clean up events older than 30 days, up to 10K at a time
CALL sp_events__cleanup(DATE_SUB(NOW(), INTERVAL 30 DAY), 10000);
```

---

## Sequencing

### sp_sequence_v2

**Purpose**: Dequeue unprocessed events and assign sequence numbers.

**Signature**:
```sql
CALL sp_sequence(
    IN p_batch_size   INT       -- Max events to sequence in one pass
);
```

**Parameters**:
- `p_batch_size`: Clamped to [1, 10000]; controls transaction size

**Behavior**:
1. Dequeues up to `p_batch_size` unprocessed events
2. Groups by partition to ensure monotonic sequence numbers per partition
3. Assigns sequence numbers using `ROW_NUMBER()` window function
4. Inserts rows into the `sequences` table with partition and sequence number
5. Deletes processed rows from `unprocessed_events`
6. Returns number of events processed (via `ROW_COUNT()`)

**Side Effects**:
- Creates rows in `sequences` table
- Deletes rows from `unprocessed_events` table
- Monotonic sequence numbers per partition are guaranteed

---

### sp_sequence_loop_v4

**Purpose**: Continuously dequeue and sequence events until queue is empty or timeout.

**Signature**:
```sql
CALL sp_sequence_loop(
    IN p_batch_size INT   -- Starting batch size (0 = read from config)
);
```

**Parameters**:
- `p_batch_size`: If 0, reads from `boxy_config: sequencer.batch.size`

**Behavior**:
1. Enters main loop; calls `sp_sequence(v_batch_size)` repeatedly
2. **Dynamic batch sizing**: Doubles batch size (up to 10000) if previous call processed a full batch
3. **Idle handling**: If no events processed, sleeps 1ms and retries (up to 60s of idle time)
4. **Timeout**: Exits after 60 seconds of continuous idle (no new events)

**Side Effects**:
- Delegates to `sp_sequence` for each iteration (cascading side effects)
- Runs continuously for ~60 seconds then exits

**Configuration**:
- `boxy_config: sequencer.batch.size` (default 1000)

---

## Consumer Management

### sp_consumers__register

**Purpose**: Register a new consumer or update heartbeat for an existing one.

**Signature**:
```sql
CALL sp_consumers__register(
    IN p_consumer_id VARCHAR(36),       -- Consumer identifier
    IN p_subscription_id BIGINT,        -- Subscription to join
    IN p_topic_ids JSON,                -- JSON array of topic IDs
    IN p_heartbeat_interval DOUBLE      -- Heartbeat interval in seconds
);
```

**Parameters**:
- `p_consumer_id`: Unique identifier for the consumer
- `p_subscription_id`: Subscription the consumer will join
- `p_topic_ids`: JSON array of topic IDs to subscribe to (e.g., `[1, 2, 3]`)
- `p_heartbeat_interval`: Heartbeat frequency (default 3 seconds)

**Behavior**:
1. Inserts or updates row in `consumers` table
2. Sets `heartbeat_deadline = NOW() + INTERVAL p_heartbeat_interval SECOND`
3. Creates cursors for all partitions of subscribed topics (via `sp_subscriptions__subscribe`)

**Side Effects**:
- Creates/updates consumer record
- Creates cursor records for all topic partitions

---

### sp_consumers__deregister

**Purpose**: Deregister a consumer and clean up its leases.

**Signature**:
```sql
CALL sp_consumers__deregister(
    IN p_consumer_id VARCHAR(36)   -- Consumer to deregister
);
```

**Behavior**:
1. Finds all cursors owned by the consumer
2. Deletes corresponding consumer_leases rows
3. Deletes the consumer record
4. Does NOT delete cursors (preserved for other consumers)

---

### sp_consumers__gc_v3

**Purpose**: Garbage-collect expired consumers.

**Signature**:
```sql
CALL sp_consumers__gc();
```

**Behavior**:
1. Finds all consumers with `heartbeat_deadline < NOW()`
2. Deletes their consumer_leases rows
3. Deletes the consumer records
4. Returns count of deleted consumers

**Side Effects**:
- Removes dead consumers and releases their leases
- Typically called every 10 seconds by MySQL event scheduler

---

## Cursor Management

### sp_cursors__commit_v2

**Purpose**: Durably advance cursor positions and release leases.

**Signature**:
```sql
CALL sp_cursors__commit(
    IN p_consumer_id VARCHAR(36),       -- Consumer advancing positions
    IN p_cursor_positions JSON          -- Map of cursor_id → position
);
```

**Parameters**:
- `p_consumer_id`: Consumer identifier
- `p_cursor_positions`: JSON object mapping cursor IDs to positions:
  ```json
  {
    "123": 456,
    "124": 789
  }
  ```

**Behavior**:
1. Validates input is a JSON object
2. Expands JSON into temporary table
3. Updates `cursors` table with new positions
4. Releases leases by nulling `consumer_leases.locked_until` (only for this consumer)
5. Returns number of cursors updated

**Error Codes**:
- `CURSOR_POSITIONS_NOT_OBJECT`: `p_cursor_positions` is not a JSON object

**Side Effects**:
- Advances cursor positions in the `cursors` table
- Releases leases immediately (other consumers can acquire)
- Preserves `last_read_position` in `consumer_leases` for consistency

---

## Subscription Management

### sp_subscriptions__subscribe_v3

**Purpose**: Subscribe a topic to a subscription.

**Signature**:
```sql
CALL sp_subscriptions__subscribe(
    IN p_subscription_id BIGINT,      -- Subscription to update
    IN p_topic_id BIGINT,             -- Topic to subscribe to
    IN p_heartbeat_interval DOUBLE    -- Heartbeat interval (from boxy_config)
);
```

**Behavior**:
1. Creates a row in `subscription_topics`
2. Creates cursors for each partition of the topic
3. Initializes each cursor with `position = partition.high_watermark`

**Side Effects**:
- Creates `subscription_topics` entry
- Creates `cursors` entries for all topic partitions

---

### sp_subscriptions__create

**Purpose**: Create a new subscription.

**Signature**:
```sql
CALL sp_subscriptions__create(
    IN p_name VARCHAR(500)   -- Subscription name
);
```

**Behavior**:
1. Inserts row into `subscriptions` table
2. Returns subscription ID (via `LAST_INSERT_ID()`)

---

### sp_subscriptions__delete_v2

**Purpose**: Delete a subscription and clean up related data.

**Signature**:
```sql
CALL sp_subscriptions__delete(
    IN p_subscription_id BIGINT   -- Subscription to delete
);
```

**Behavior**:
1. Deletes all consumer_leases for cursors in this subscription
2. Deletes all cursors for this subscription
3. Deletes all subscription_topics entries
4. Deletes the subscription itself

---

## Topic Management

### sp_topics__create

**Purpose**: Create a new topic.

**Signature**:
```sql
CALL sp_topics__create(
    IN p_namespace_id BIGINT,       -- Namespace for the topic
    IN p_topic_name VARCHAR(500),   -- Topic name
    IN p_partition_count INT        -- Number of partitions
);
```

**Behavior**:
1. Inserts row into `topics` table
2. Creates `p_partition_count` partition rows
3. Returns topic ID

---

### sp_topics__cache_get

**Purpose**: Resolve namespace path and topic name to IDs (with caching).

**Signature**:
```sql
CALL sp_topics__cache_get(
    IN p_path VARCHAR(4000),
    IN p_topic VARCHAR(500),
    OUT p_topic_id BIGINT,
    OUT p_partitions INT
);
```

**Parameters** (OUT):
- `p_topic_id`: Resolved topic ID
- `p_partitions`: Partition count for the topic

**Behavior**:
1. Hashes the namespace path using MD5
2. Joins with `namespaces` and `topics` tables
3. Returns topic ID and partition count

---

### sp_topics__delete

**Purpose**: Delete a topic and its partitions.

**Signature**:
```sql
CALL sp_topics__delete(
    IN p_topic_id BIGINT   -- Topic to delete
);
```

**Behavior**:
1. Deletes all partitions for the topic
2. Deletes the topic itself
3. Does NOT delete events or sequences (data persists)

---

## Namespace Management

### sp_namespaces__create

**Purpose**: Create a namespace.

**Signature**:
```sql
CALL sp_namespaces__create(
    IN p_parent_id BIGINT,       -- Parent namespace ID (NULL for root)
    IN p_name VARCHAR(500)       -- Namespace name
);
```

---

### sp_namespaces__delete, sp_namespaces__rename, sp_namespaces__move

Similar lifecycle procedures for namespace management. See schema.md for details.

---

## Configuration Management

### boxy_config Table

Configuration is stored as key-value pairs in the `boxy_config` table:

**Schema**:
```sql
CREATE TABLE boxy_config (
    config_key VARCHAR(255) NOT NULL PRIMARY KEY,
    config_value VARCHAR(1000) NOT NULL
);
```

**Standard Keys**:

| Key | Default | Type | Description |
|-----|---------|------|-------------|
| `max.event.payload.bytes` | 1048576 | Integer | Maximum payload size in bytes |
| `max.batch.size` | 1000 | Integer | Maximum events in publish batch |
| `sequencer.batch.size` | 1000 | Integer | Sequencer batch size |
| `poll.batch.size` | 100 | Integer | Consumer poll batch size |
| `lease.lock.seconds` | 3 | Integer | Cursor lease duration |
| `heartbeat.interval.seconds` | 3 | Integer | Consumer heartbeat interval |
| `sequencer.interval.seconds` | 10 | Integer | Sequencer event schedule (read-only) |
| `consumer_gc.interval.seconds` | 10 | Integer | GC event schedule (read-only) |
| `events.retain.days` | 30 | Integer | Retention window for cleanup |

**Runtime Updates**:
```sql
UPDATE boxy_config SET config_value = '2000' WHERE config_key = 'sequencer.batch.size';
-- Takes effect on next event dequeue
```

---

## Error Code Reference

| Code | Procedure(s) | Meaning | Resolution |
|------|--------------|---------|-----------|
| `UNKNOWN_CONSUMER` | poll | Consumer ID not found | Register the consumer first |
| `UNKNOWN_TOPIC` | publish, publish_multi | Topic does not exist | Create the topic |
| `UNKNOWN_NAMESPACE` | publish, publish_multi | Namespace does not exist | Create the namespace |
| `PAYLOAD_TOO_LARGE` | publish, publish_multi | Event exceeds size limit | Increase `max.event.payload.bytes` or reduce payload |
| `BATCH_TOO_LARGE` | publish_multi | Batch exceeds size limit | Increase `max.batch.size` or split batch |
| `CURSOR_POSITIONS_NOT_OBJECT` | commit | JSON is not an object | Pass a JSON object, not an array |

---

## Helper Functions

### fn_random_int()

Returns a random integer for random-start polling in `sp_events__poll`.

**Signature**:
```sql
SELECT fn_random_int() AS random_key;
```

### fn_resolve_partition_id(topic_id, partition_number)

Resolves a partition number to a partition ID.

**Signature**:
```sql
SELECT fn_resolve_partition_id(topic_id, partition_number) AS partition_id;
```

---

## Related Documentation

- [Database Schema](schema.md) — Table and column definitions
- [Retention Strategy](retention.md) — Event cleanup procedures
- [Configuration](configuration.md) — Configuration keys
