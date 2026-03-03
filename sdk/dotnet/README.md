# Boxy .NET Client SDK

C# / .NET client SDK for Boxy event queue. Provides async event consumption with Reactive Extensions support.

## Status

**Scaffold/Evaluation** — Basic structure and interface definitions. Full implementation deferred to future milestone.

## Features (Planned)

- Async event consumption via Task-based APIs
- Automatic cursor commits
- Reactive Extensions (Rx.NET) support for advanced scenarios
- OpenTelemetry metrics and tracing
- Configurable batch sizes and polling intervals
- Connection pooling via `MySqlConnector`
- Dependency injection support (Microsoft.Extensions.DependencyInjection)

## Architecture

```
Boxy.Client/
  Consumer.cs              # Main consumer class (async polling)
  Worker.cs                # Background worker implementation
  ConsumerConfig.cs        # Configuration builder
  EventModel.cs            # Event, Cursor records
  BoxyException.cs         # Custom exceptions
  Metrics.cs               # OpenTelemetry setup
```

## Example Usage (Planned)

```csharp
using Boxy.Client;
using Microsoft.Extensions.DependencyInjection;

var services = new ServiceCollection()
    .AddBoxy("Server=localhost;User=root;Password=root;Database=boxy")
    .AddLogging()
    .BuildServiceProvider();

var config = new ConsumerConfig
{
    SubscriptionName = "my-group",
    Topic = "events",
    Path = "/",
    MaxBatchSize = 50,
    Concurrency = 4,
    AutoCommit = true
};

var consumer = services.GetRequiredService<IConsumer>();
await consumer.StartAsync(
    config,
    events => HandleEventsAsync(events),
    CancellationToken.None
);

async Task<bool> HandleEventsAsync(IReadOnlyList<Event> events)
{
    foreach (var @event in events)
    {
        Console.WriteLine($"Event: {@event.Key} -> {@event.Payload}");
    }
    return true;
}
```

## Installation (Planned)

```bash
dotnet add package Boxy.Client
```

## Stored Procedures Used

- `sp_consumers__register(subscription)` — Register consumer group
- `sp_subscriptions__subscribe(subscription, path, topic)` — Subscribe to topic
- `sp_events__poll(subscription, path, topic, max_count, timeout)` — Poll events
- `sp_cursors__commit(subscription, path, topic, partition, position)` — Commit cursor

## Configuration

| Option | Default | Type | Description |
|--------|---------|------|-------------|
| `SubscriptionName` | — | string | Consumer group name (required) |
| `Topic` | — | string | Topic name (required) |
| `Path` | `/` | string | Namespace path |
| `MaxBatchSize` | 50 | int | Events per poll |
| `PollTimeoutMs` | 5000 | int | Poll database timeout (milliseconds) |
| `Concurrency` | 4 | int | Number of concurrent tasks |
| `AutoCommit` | true | bool | Automatically commit cursors |

## Testing

```bash
cd ../../docker && docker-compose up -d && cd -
dotnet test
cd ../../docker && docker-compose down
```

## Implementation Notes

1. Use `MySqlConnector` for async MySQL connections
2. Use `Task`-based async APIs throughout (no `.Result` calls)
3. Use `IAsyncEnumerable<T>` for streaming event batches
4. Use `OpenTelemetry` for metrics and tracing
5. Support `IAsyncDisposable` for graceful cleanup
6. Implement exponential backoff for poll retries via `Polly` library
7. Support custom event deserializers via `IEventDeserializer<T>` interface
8. Use `ILogger` for structured logging

## Dependencies (Planned)

- `MySqlConnector` — Async MySQL ADO.NET provider
- `OpenTelemetry` — Observability
- `Polly` — Resilience and transient fault handling
- `Microsoft.Extensions.DependencyInjection` — IoC container
- `Microsoft.Extensions.Logging` — Logging abstraction
- `System.Reactive` — Reactive Extensions (optional)

## Example: Reactive Extensions Usage (Planned)

```csharp
var eventStream = consumer.GetEventStreamAsync(config);

eventStream
    .Buffer(TimeSpan.FromSeconds(10), 100)  // Buffer events
    .SelectMany(batch => ProcessBatchAsync(batch))
    .Subscribe(
        onNext: result => Console.WriteLine($"Processed: {result}"),
        onError: error => Console.Error.WriteLine($"Error: {error}")
    );
```

## Framework Support

- `.NET 6.0` and later
- `.NET Framework 4.7.2` (via compatibility shim, planned)

## Performance Considerations

- Connection pooling via `MySqlConnectorOptions`
- Batch polling to reduce round-trips
- Lock-free concurrent polling with channels
- Memory pooling for buffer reuse

## See Also

- [Boxy Core](../../boxy-core/)
- [Boxy Architecture](../../docs/)
- [Release Process](../../docs/release-process.md)
