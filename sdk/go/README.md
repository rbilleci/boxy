# Boxy Go Client SDK

Go client SDK for Boxy event queue. Provides async event consumption with back-pressure and metrics support.

## Status

**Scaffold/Evaluation** — Basic structure and interface definitions. Full implementation deferred to future milestone.

## Features (Planned)

- Async event consumption via goroutines
- Automatic cursor commits
- Graceful shutdown with context cancellation
- Metrics via Prometheus client library
- Configurable batch sizes and polling intervals
- Connection pooling via `database/sql`

## Architecture

```
boxy/
  consumer.go       # Consumer interface (Connect, Poll, Commit)
  worker.go         # Async worker (goroutine-based polling)
  config.go         # Consumer configuration
  types.go          # Event, Cursor records
  errors.go         # Error types
```

## Example Usage (Planned)

```go
import "github.com/rbilleci/boxy-go"

config := boxy.NewConsumerConfig("my-group", "events").
    WithPath("/").
    WithBatchSize(50).
    WithConcurrency(4).
    Build()

consumer, err := boxy.NewConsumer("mysql://user:pass@localhost/boxy", config)
if err != nil {
    log.Fatal(err)
}

consumer.Start(ctx, func(events []boxy.Event) error {
    for _, event := range events {
        fmt.Println("Event:", event.Key, event.Payload)
    }
    return nil
})

<-ctx.Done()
consumer.Stop(ctx)
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
| `poll_timeout` | 5s | Poll database timeout |
| `concurrency` | 4 | Number of goroutines |
| `auto_commit` | true | Automatically commit cursors |

## Testing

```bash
docker-compose -f ../../docker/docker-compose.yml up -d
go test -v ./...
docker-compose down
```

## Implementation Notes

1. Use `database/sql` for connection pooling (no ORM)
2. Use `context.Context` for cancellation and timeout
3. Use `prometheus/client_golang` for metrics
4. Group events by partition for efficient cursor commits
5. Implement exponential backoff for poll retries
6. Support custom deserializers for payload types

## See Also

- [Boxy Core](../../boxy-core/)
- [Boxy Architecture](../../docs/)
- [Release Process](../../docs/release-process.md)
