# Boxy Quickstart Guide

Get up and running with Boxy in minutes: publish events, consume them, and scale.

## Prerequisites

- **MySQL 8.0.23+**: Database server with event scheduler support
- **Java 21+**: JDK for compiling and running Boxy
- **Maven 3.8.1+**: Build tool

## Installation

### 1. Add Boxy Dependency

Add the following to your Maven `pom.xml`:

```xml
<dependency>
    <groupId>com.example.boxy</groupId>
    <artifactId>boxy-mysql</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- For Micrometer metrics (optional, but recommended) -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
    <version>1.14.5</version>
</dependency>
```

### 2. Initialize the Database

Run Liquibase migrations to set up the schema:

```bash
# Set your database connection variables
export DB_HOST=localhost
export DB_PORT=3306
export DB_NAME=events_db
export DB_USER=root
export DB_PASSWORD=password

# Run migrations
mvn -f boxy-db/pom.xml liquibase:update
```

Verify the schema was created:

```sql
mysql -h $DB_HOST -u $DB_USER -p$DB_PASSWORD $DB_NAME \
  -e "SHOW TABLES;" | grep -E "events|consumers|subscriptions"
```

Expected output:
```
consumers
consumer_leases
cursors
events
namespaces
partitions
sequences
subscriptions
subscription_topics
topics
unprocessed_events
```

### 3. Create a Namespace and Topic

```sql
mysql -h $DB_HOST -u $DB_USER -p$DB_PASSWORD $DB_NAME << 'SQL'
-- Create a root namespace
CALL sp_namespaces__create(NULL, 'example');

-- Create a topic with 4 partitions
CALL sp_topics__create(
    (SELECT id FROM namespaces WHERE name = 'example'),
    'orders',
    4
);
SQL
```

## Producer: Publish Events

Here's a complete example of publishing events:

```java
import boxy.mysql.DataSourceProvider;
import boxy.mysql.repository.EventRepository;
import boxy.core.domain.PublishRequest;

public class OrderProducer {
    public static void main(String[] args) throws Exception {
        // Initialize the data source
        var dataSource = DataSourceProvider.dataSource();
        var eventRepo = new EventRepository(dataSource);

        // Publish a single event
        eventRepo.publish(
            "example",              // namespace path
            "orders",               // topic name
            "order-12345",          // routing key (determines partition)
            "{\"order_id\": 12345, \"amount\": 99.99}"  // payload
        );
        System.out.println("Published single event");

        // Publish multiple events (batch)
        var events = List.of(
            new PublishRequest("example", "orders", "order-12346", "{\"order_id\": 12346, \"amount\": 150.00}"),
            new PublishRequest("example", "orders", "order-12347", "{\"order_id\": 12347, \"amount\": 75.50}"),
            new PublishRequest("example", "orders", "order-12348", "{\"order_id\": 12348, \"amount\": 200.00}")
        );
        eventRepo.publishBatch(events);
        System.out.println("Published batch of " + events.size() + " events");
    }
}
```

**Build and run**:

```bash
javac -cp "boxy-mysql-1.0.0.jar:..." OrderProducer.java
java -cp ".:boxy-mysql-1.0.0.jar:..." OrderProducer
```

## Consumer: Poll and Commit

Here's a complete consumer example:

```java
import boxy.mysql.DataSourceProvider;
import boxy.mysql.repository.ConsumerRepository;
import boxy.mysql.repository.CursorRepository;
import boxy.mysql.repository.SubscriptionRepository;
import boxy.core.domain.Event;
import java.util.Map;

public class OrderConsumer {
    public static void main(String[] args) throws Exception {
        var dataSource = DataSourceProvider.dataSource();
        var subRepo = new SubscriptionRepository(dataSource);
        var consumerRepo = new ConsumerRepository(dataSource);
        var cursorRepo = new CursorRepository(dataSource);

        // Create a subscription
        var subscriptionId = subRepo.create("order-processing");
        System.out.println("Created subscription: " + subscriptionId);

        // Subscribe to topics
        // (get topic ID from database)
        var topicId = 1L;  // example; query the database to get the real ID
        consumerRepo.subscribe(subscriptionId, List.of(topicId));
        System.out.println("Subscribed to topic: " + topicId);

        // Register the consumer
        var consumerId = "order-processor-1";
        consumerRepo.register(consumerId, subscriptionId, List.of(topicId));
        System.out.println("Registered consumer: " + consumerId);

        // Poll for events in a loop
        var cursorPositions = new ConcurrentHashMap<Long, Long>();
        while (true) {
            var pollResult = consumerRepo.poll(consumerId, 100);
            if (pollResult.isEmpty()) {
                System.out.println("No events available; sleeping...");
                Thread.sleep(1000);
                continue;
            }

            System.out.println("Polled " + pollResult.size() + " events");

            // Process each event
            for (var event : pollResult) {
                processEvent(event, cursorPositions);
            }

            // Commit positions
            cursorRepo.commit(consumerId, cursorPositions);
            System.out.println("Committed cursor positions");
        }
    }

    private static void processEvent(Event event, Map<Long, Long> positions) {
        System.out.println("Processing event: " + event.data());
        // Parse and handle the event...

        // Track the cursor position
        positions.put(event.cursorId(), event.sequence());
    }
}
```

**Build and run**:

```bash
javac -cp "boxy-mysql-1.0.0.jar:..." OrderConsumer.java
java -cp ".:boxy-mysql-1.0.0.jar:..." OrderConsumer
```

## End-to-End Example

Here's a self-contained example that sets up and runs both producer and consumer:

```java
import boxy.mysql.DataSourceProvider;
import boxy.mysql.repository.*;
import boxy.core.domain.PublishRequest;
import java.util.*;
import java.util.concurrent.*;

public class BoxyQuickstart {
    public static void main(String[] args) throws Exception {
        var ds = DataSourceProvider.dataSource();
        var nsRepo = new NamespaceRepository(ds);
        var topicRepo = new TopicRepository(ds);
        var subRepo = new SubscriptionRepository(ds);
        var consumerRepo = new ConsumerRepository(ds);
        var cursorRepo = new CursorRepository(ds);
        var eventRepo = new EventRepository(ds);

        // Setup: Create namespace and topic
        System.out.println("=== Setting up ===");
        var nsId = nsRepo.create(null, "quickstart");
        System.out.println("Created namespace: " + nsId);

        var topicId = topicRepo.create(nsId, "events", 2);
        System.out.println("Created topic: " + topicId);

        // Publisher thread
        System.out.println("\n=== Publishing events ===");
        var publisherThread = new Thread(() -> {
            try {
                for (int i = 0; i < 10; i++) {
                    eventRepo.publish("quickstart", "events", "key-" + i,
                        "{\"id\": " + i + ", \"message\": \"Event " + i + "\"}");
                    Thread.sleep(100);
                }
                System.out.println("Published 10 events");
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        publisherThread.start();

        // Consumer setup
        System.out.println("\n=== Setting up consumer ===");
        var subscriptionId = subRepo.create("quickstart-subscription");
        consumerRepo.subscribe(subscriptionId, List.of(topicId));
        var consumerId = "quickstart-consumer-1";
        consumerRepo.register(consumerId, subscriptionId, List.of(topicId));
        System.out.println("Registered consumer: " + consumerId);

        // Consumer thread
        System.out.println("\n=== Consuming events ===");
        var consumerThread = new Thread(() -> {
            try {
                int eventCount = 0;
                var cursorPositions = new ConcurrentHashMap<Long, Long>();

                while (eventCount < 10) {
                    var events = consumerRepo.poll(consumerId, 100);
                    if (!events.isEmpty()) {
                        System.out.println("Polled " + events.size() + " events");
                        for (var event : events) {
                            System.out.println("  Event: " + event.data());
                            cursorPositions.put(event.cursorId(), event.sequence());
                            eventCount++;
                        }
                        cursorRepo.commit(consumerId, cursorPositions);
                    }
                    Thread.sleep(100);
                }
                System.out.println("Consumed " + eventCount + " events");
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        consumerThread.start();

        // Wait for both threads
        publisherThread.join();
        consumerThread.join();

        System.out.println("\n=== Done ===");
    }
}
```

## Configuration

### Environment Variables

Set these before running your application:

```bash
export DB_HOST=localhost
export DB_PORT=3306
export DB_NAME=events_db
export DB_USER=root
export DB_PASSWORD=password
export BOXY_LOG_LEVEL=INFO
export BOXY_POOL_SIZE=20
export BOXY_POOL_MIN_IDLE=5
```

### Tuning Parameters

Adjust for your workload via SQL:

```sql
-- Increase poll batch size for higher throughput
UPDATE boxy_config SET config_value = '500' WHERE config_key = 'poll.batch.size';

-- Increase sequencer batch size
UPDATE boxy_config SET config_value = '2000' WHERE config_key = 'sequencer.batch.size';

-- Increase lease duration for slower consumers
UPDATE boxy_config SET config_value = '5' WHERE config_key = 'lease.lock.seconds';
```

See [Configuration Tuning Guide](tuning.md) for detailed recommendations.

## Metrics and Monitoring

Enable Prometheus metrics:

```java
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import boxy.mysql.metrics.BoxyMeterRegistry;

public class MetricsSetup {
    public static void main(String[] args) {
        var prometheus = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        BoxyMeterRegistry.set(prometheus);

        // Expose metrics endpoint
        // GET /metrics → prometheus.scrape()
    }
}
```

Monitor these key metrics:

| Metric | Target | Alarm |
|--------|--------|-------|
| `boxy.publish.latency` (p99) | < 5ms | > 50ms |
| `boxy.poll.latency` (p99) | < 20ms | > 100ms |
| `boxy.sequencer.lag` | < 1000 | > 10000 |
| `hikaricp.connections.active` | < 80% of pool | > 80% |

See [Monitoring Guide](monitoring.md) for more.

## Scaling

### Add More Consumers

To scale event consumption, register multiple consumer instances:

```bash
# Instance 1
java -jar app.jar --consumer.id=consumer-1

# Instance 2
java -jar app.jar --consumer.id=consumer-2

# Instance 3
java -jar app.jar --consumer.id=consumer-3
```

Leases automatically distribute partitions across active consumers.

### Increase Topic Partitions

For higher publish throughput, add partitions:

```sql
-- Create a new topic with more partitions
CALL sp_topics__create(
    (SELECT id FROM namespaces WHERE name = 'example'),
    'orders-v2',
    16  -- More partitions
);
```

### Scale the Database

If throughput plateaus, upgrade the database instance:

```bash
# From db.r6g.2xlarge to db.r6g.4xlarge
# Increase BOXY_POOL_SIZE accordingly
export BOXY_POOL_SIZE=40
```

## Troubleshooting

### Consumer not receiving events

```sql
-- Check if consumer is registered
SELECT id, subscription_id, heartbeat_deadline FROM consumers WHERE id = 'consumer-1';

-- Check cursor positions
SELECT id, subscription_id, topic_id, partition_id, position FROM cursors;

-- Check for leases
SELECT consumer_id, cursor_id, locked_until FROM consumer_leases;
```

### Sequencer not processing events

```sql
-- Check unprocessed_events backlog
SELECT COUNT(*) FROM unprocessed_events;

-- Check if sequencer event is running
SHOW EVENTS LIKE 'sequencer%';

-- Check for errors
SELECT error_time, error_message FROM background_job_errors 
WHERE job_name = 'sequencer' ORDER BY error_time DESC LIMIT 5;
```

### Connection pool exhaustion

```bash
# Increase pool size
export BOXY_POOL_SIZE=40
export BOXY_POOL_MIN_IDLE=10

# Restart application
```

## Next Steps

- [Configuration Tuning Guide](tuning.md) — Optimize for your workload
- [Operational Runbook](runbook.md) — Run Boxy in production
- [Monitoring Guide](monitoring.md) — Set up observability
- [API Reference](stored-procedures.md) — Complete procedure documentation

## Related Documentation

- [README](../README.md) — Project overview
- [Architecture](../PRODUCTION_READINESS.md) — Design decisions
- [Database Schema](schema.md) — Table structure
