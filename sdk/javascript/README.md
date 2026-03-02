# Boxy JavaScript/Node.js Client SDK

JavaScript/TypeScript client SDK for Boxy event queue. Provides async event consumption with Promise and async/await support.

## Status

**Scaffold/Evaluation** — Basic structure and interface definitions. Full implementation deferred to future milestone.

## Features (Planned)

- Async event consumption via async/await
- Automatic cursor commits
- TypeScript support with full type safety
- Prometheus metrics via `prom-client`
- Configurable batch sizes and polling intervals
- Connection pooling via `mysql2/promise`
- ESM and CommonJS support

## Architecture

```
src/
  index.ts          # Public API exports
  Consumer.ts       # Main consumer class
  Worker.ts         # Background worker
  Config.ts         # Configuration types/builder
  types.ts          # Event, Cursor types
  errors.ts         # Error types
  metrics.ts        # Prometheus metrics setup
  pool.ts           # Connection pool wrapper
```

## Example Usage (Planned)

```typescript
import { Consumer, ConsumerConfig } from 'boxy-client';

const config: ConsumerConfig = {
  subscriptionName: 'my-group',
  topic: 'events',
  path: '/',
  maxBatchSize: 50,
  concurrency: 4,
  autoCommit: true,
};

const consumer = new Consumer(
  'mysql://user:pass@localhost/boxy',
  config
);

consumer.start(async (events) => {
  for (const event of events) {
    console.log(`Event: ${event.key} -> ${event.payload}`);
  }
  return true; // acknowledge batch
});

process.on('SIGTERM', async () => {
  await consumer.stop();
  process.exit(0);
});
```

## Installation (Planned)

```bash
npm install boxy-client
# or
yarn add boxy-client
```

## Stored Procedures Used

- `sp_consumers__register(subscription)` — Register consumer group
- `sp_subscriptions__subscribe(subscription, path, topic)` — Subscribe to topic
- `sp_events__poll(subscription, path, topic, max_count, timeout)` — Poll events
- `sp_cursors__commit(subscription, path, topic, partition, position)` — Commit cursor

## Configuration

| Option | Default | Type | Description |
|--------|---------|------|-------------|
| `subscriptionName` | — | string | Consumer group name (required) |
| `topic` | — | string | Topic name (required) |
| `path` | `/` | string | Namespace path |
| `maxBatchSize` | 50 | number | Events per poll |
| `pollTimeoutMs` | 5000 | number | Poll database timeout |
| `concurrency` | 4 | number | Number of async workers |
| `autoCommit` | true | boolean | Automatically commit cursors |

## Testing

```bash
cd ../../docker && docker-compose up -d && cd -
npm test
cd ../../docker && docker-compose down
```

## Implementation Notes

1. Use `mysql2/promise` for async MySQL connections
2. Use native async/await throughout (no callbacks)
3. Use `AsyncIterator` or `AsyncIterable` for event streams
4. Use `prom-client` for Prometheus metrics
5. Support graceful shutdown via `AbortSignal`
6. Implement exponential backoff for poll retries
7. Support custom event deserializers
8. Use structured logging via `winston` or `pino`

## Dependencies (Planned)

```json
{
  "mysql2": "^3.0.0",
  "prom-client": "^15.0.0",
  "winston": "^3.10.0"
}
```

Dev dependencies:

```json
{
  "typescript": "^5.0.0",
  "@types/node": "^20.0.0",
  "jest": "^29.0.0",
  "@types/jest": "^29.0.0"
}
```

## TypeScript Example

```typescript
interface EventBatch<T> {
  events: Event<T>[];
  partition: number;
  maxSequence: bigint;
}

async function processEvents(batch: EventBatch<string>): Promise<boolean> {
  console.log(`Processing ${batch.events.length} events from partition ${batch.partition}`);
  
  for (const event of batch.events) {
    console.log(`  Key: ${event.key}, Payload: ${event.payload}`);
  }
  
  return true;
}

const consumer = new Consumer<string>(
  'mysql://user:pass@localhost/boxy',
  config
);

consumer.on('error', (err) => console.error('Consumer error:', err));
consumer.on('metrics', (metrics) => console.log('Metrics:', metrics));

await consumer.start(processEvents);
```

## Metrics

Exposed via Prometheus:

```
# HELP boxy_consumer_events_processed_total Total events processed
# TYPE boxy_consumer_events_processed_total counter
boxy_consumer_events_processed_total{topic="events",subscription="my-group"} 12345

# HELP boxy_consumer_errors_total Total errors encountered
# TYPE boxy_consumer_errors_total counter
boxy_consumer_errors_total{topic="events",subscription="my-group"} 2

# HELP boxy_consumer_poll_duration_seconds Event polling duration
# TYPE boxy_consumer_poll_duration_seconds histogram
boxy_consumer_poll_duration_seconds_bucket{...} 42
```

## Browser Support

Not recommended for browser environments. Node.js 16+ required.

## See Also

- [Boxy MySQL Driver](../../boxy-mysql/)
- [Boxy Architecture](../../docs/)
- [Release Process](../../docs/release-process.md)
