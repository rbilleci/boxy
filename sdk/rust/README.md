# Boxy Rust Client SDK

Rust client SDK for Boxy event queue. Provides async event consumption with zero-copy where possible.

## Status

**Scaffold/Evaluation** — Basic structure and interface definitions. Full implementation deferred to future milestone.

## Features (Planned)

- Async event consumption via Tokio
- Automatic cursor commits
- Type-safe event handling via enums
- Metrics via Prometheus client
- Configurable batch sizes and polling intervals
- Connection pooling via `sqlx` or `mysql_async`
- Zero-copy payload handling (bytes references)

## Architecture

```
src/
  lib.rs            # Public API
  consumer.rs       # Consumer struct (async polling)
  worker.rs         # Worker for background polling
  config.rs         # Consumer configuration
  types.rs          # Event, Cursor records
  errors.rs         # Error types
  metrics.rs        # Prometheus metrics
  pool.rs           # Connection pool wrapper
```

## Example Usage (Planned)

```rust
use boxy::{ConsumerConfig, Consumer};

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    let config = ConsumerConfig::builder()
        .subscription_name("my-group")
        .topic("events")
        .path("/")
        .max_batch_size(50)
        .concurrency(4)
        .auto_commit(true)
        .build()?;

    let consumer = Consumer::new(
        "mysql://user:pass@localhost/boxy",
        config
    ).await?;

    consumer.start(|events| async {
        for event in events {
            println!("Event: {:?} -> {:?}", event.key, event.payload);
        }
        Ok(true)
    }).await?;

    Ok(())
}
```

## Installation (Planned)

Add to Cargo.toml:

```toml
[dependencies]
boxy-client = "1.0"
tokio = { version = "1", features = ["full"] }
sqlx = { version = "0.7", features = ["mysql", "runtime-tokio"] }
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
| `poll_timeout_secs` | 5 | Poll database timeout |
| `concurrency` | 4 | Number of Tokio tasks |
| `auto_commit` | true | Automatically commit cursors |

## Testing

```bash
cd ../../docker && docker-compose up -d && cd -
cargo test
cd ../../docker && docker-compose down
```

## Implementation Notes

1. Use `sqlx` for compile-time SQL verification (via macros)
2. Use `tokio::task` for concurrent polling
3. Use `prometheus` crate for metrics
4. Group events by partition for efficient cursor commits
5. Implement exponential backoff for poll retries
6. Support custom deserializers via traits
7. Use `Bytes` for zero-copy payload handling
8. Strong type system for event validation

## Dependencies (Planned)

- `tokio` — Async runtime
- `sqlx` — SQL toolkit with compile-time checking
- `prometheus` — Prometheus client
- `thiserror` — Ergonomic error handling
- `serde` — Serialization framework
- `tracing` — Structured logging
- `bytes` — Efficient byte buffer handling

## Performance Considerations

- Connection pooling via `sqlx::MySqlPoolOptions`
- Batch polling to reduce round-trips
- Zero-copy payload handling where possible
- Lock-free concurrent polling

## See Also

- [Boxy MySQL Driver](../../boxy-mysql/)
- [Boxy Architecture](../../docs/)
- [Release Process](../../docs/release-process.md)
