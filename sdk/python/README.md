# Boxy Python Client SDK

Python client SDK for Boxy event queue. Provides async event consumption with back-pressure and metrics support.

## Status

**Scaffold/Evaluation** — Basic structure and interface definitions. Full implementation deferred to future milestone.

## Features (Planned)

- Async event consumption via asyncio
- Automatic cursor commits
- Graceful shutdown support
- Metrics via Prometheus client
- Configurable batch sizes and polling intervals
- Connection pooling via `mysql-connector-python` or `aiomysql`

## Architecture

```
boxy/
  __init__.py       # Package exports
  consumer.py       # Consumer class (async polling)
  worker.py         # Worker for background polling
  config.py         # Consumer configuration
  types.py          # Event, Cursor records
  errors.py         # Error types
  metrics.py        # Prometheus metrics
```

## Example Usage (Planned)

```python
import asyncio
from boxy import ConsumerConfig, Consumer

config = ConsumerConfig(
    subscription_name="my-group",
    topic="events",
    path="/",
    max_batch_size=50,
    concurrency=4,
    auto_commit=True
)

async def handle_events(events):
    for event in events:
        print(f"Event: {event.key} -> {event.payload}")
    return True

consumer = Consumer("mysql://user:pass@localhost/boxy", config)

async def main():
    await consumer.start(handle_events)
    await asyncio.sleep(60)  # Run for 60 seconds
    await consumer.stop()

if __name__ == "__main__":
    asyncio.run(main())
```

## Installation (Planned)

```bash
pip install boxy-client
```

## Stored Procedures Used

- `sp_consumers__register(subscription)` — Register consumer group
- `sp_subscriptions__subscribe(subscription, path, topic)` — Subscribe to topic
- `sp_events__poll(subscription, path, topic, max_count, timeout)` — Poll events
- `sp_cursors__commit(subscription, path, topic, partition, position)` — Commit cursor

## Configuration

| Option | Default | Description |
|--------|---------|-------------|
| `path` | `/` | Namespace path |
| `max_batch_size` | 50 | Events per poll |
| `poll_timeout_seconds` | 5 | Poll database timeout |
| `concurrency` | 4 | Number of async tasks |
| `auto_commit` | true | Automatically commit cursors |

## Testing

```bash
docker-compose -f ../../docker/docker-compose.yml up -d
pytest tests/
docker-compose down
```

## Implementation Notes

1. Use `aiomysql` for async MySQL connections
2. Use `asyncio.gather()` for concurrent polling
3. Use `prometheus_client` for metrics
4. Group events by partition for efficient cursor commits
5. Implement exponential backoff for poll retries
6. Support custom deserializers for payload types
7. Handle `asyncio.CancelledError` for graceful shutdown

## Dependencies (Planned)

- `aiomysql>=0.1.0` — Async MySQL driver
- `prometheus-client>=0.16.0` — Prometheus metrics
- `pydantic>=2.0.0` — Data validation
- `structlog>=22.0.0` — Structured logging

## See Also

- [Boxy MySQL Driver](../../boxy-mysql/)
- [Boxy Architecture](../../docs/)
- [Release Process](../../docs/release-process.md)
