
# Boxy
<img src="docs/images/boxy-logo.png" alt="Boxy Logo" style="width:50%" align="right"/>

Boxy is a multi-tenant event streaming library that exposes Apache Pulsar-like semantics 
directly over a database’s transactional outbox. It targets monolithic applications that need event streaming without 
taking on the operational cost of running a Pulsar/Kafka deployment. 
Boxy turns your transactional outbox into an event stream and allows you to connect asynchronous consumers in your 
favorite programming language. 
Tenant isolation is provided through a hierarchical namespace system.

## Table of Contents

- [Design Highlights](#design-highlights)
- [Architecture Decisions](#architecture-decisions)
- [Limits](#limits)
- [Boxy Core and Boxy DB Overview](#boxy-core-and-boxy-db-overview)
  - [Key Tables and Views](#key-tables-and-views)
  - [Subscription Statistics](#subscription-statistics)
- [Domain Classes](#domain-classes)
- [Heartbeats](#heartbeats)
- [Client Consumer Protocol](#client-consumer-protocol)
  - [Consumer Stored Procedures](#consumer-stored-procedures)
    - [Registration Procedure](#registration-procedure)
    - [Polling Procedure](#polling-procedure)
    - [Commit Procedure](#commit-procedure)
    - [Deregistration Procedure](#deregistration-procedure)
  - [Lifecycle and state names](#lifecycle-and-state-names)
- [Building the Project](#building-the-project)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Database Setup](#database-setup)
- [FAQ](#faq)
  - [If my application models multi-tenancy using a database schema per tenant, should I install boxy in each tenant?](#if-my-application-models-multi-tenancy-using-a-database-schema-per-tenant-should-i-install-boxy-in-each-tenant)
  - [How is the schema managed?](#how-is-the-schema-managed)
- [Contributing](#contributing)
- [License](#license)

## Design Highlights

- **Fair-share Load Balancing** across consumers, proportional to capacity weights
- **Low Consumer Lag**: p99 ~5ms for active partitions, and ~100ms for cold partitions
- **Scalable Polling**: consumers stagger check-ins to minimize database queries (e.g. ≈10 checks/s instead of hundreds)

## Architecture Decisions
- APIs for consumers and producers are designed to be easy to use.
- Publishing an event should be possible when only knowing the path and topic name.
- Third-party libraries are minimized.
- For safety: boxy never deletes events. Event deletion is left to be orchestrated by you.
- For easy portability across programming languages and runtimes, all mutations are strictly performed by stored procedures.
- Namespace and Topic Names are case-sensitive.

## Limits
- Each topic has a practical limit of 1024 partitions, and a technical limit of 65536 partitions
- Each subscription has a practical limit of 1024 consumers.
- The fully qualified namespace path and topic name has a limit of 4000 characters
- Each namespace name a limit of 500 characters.
- Each topic name has a limit of 500 characters.

---

## Boxy Core and Boxy DB Overview

Liquibase migrations for the schema are under `boxy-db/src/main/resources/db/changelog`. The schema models:

```mermaid
    erDiagram
        namespaces ||--o{ topics : owns
        topics ||--o{ partitions : has
        partitions ||--o{ unprocessed_events : queues
        partitions ||--o{ sequences : sequences
        events ||--|| unprocessed_events : references
        events ||--|| sequences : references
        subscriptions ||--o{ subscription_topics : links
        subscriptions ||--o{ cursors : positions
        subscription_topics ||--o{ cursors : positions
```

### Key Tables and Views

- **namespaces**: hierarchical containers for topics.
- **topics**: belong to namespaces and declare a partition count.
- **partitions**: per-topic shards that track a `high_watermark`.
  - **events**: raw event payloads; partition and sequence metadata are tracked separately.
  - **unprocessed_events**: queue linking newly published events to partitions until sequenced.
  - **sequences**: per-partition sequence numbers referencing single events (one row per event).
- **subscription_topics**: links subscriptions to the topics they consume and stores precomputed statistics.
- **cursors**: tracks the position per subscription and partition and stores the `subscription_id`,
  `subscription_topic_id`, and `topic_id` for join-free lookups. Each row is assigned a persistent
  `random_key` used for evenly distributing start positions among consumers.
- **consumers**: registers each consumer’s `subscription_id`, `weight`, `heartbeat_detected_at`, and `topic_ids` JSON array of topic identifiers.
- **subscriptions**: defines logical groups of consumers.
- **topics_cache**: in-memory table for quick topic lookups.

### Subscription Statistics

The `subscription_topics` table stores precomputed statistics that are updated with each consumer check-in:

- **heartbeat_interval**: Adaptive interval used for consumer heartbeats.
- **active_partitions**: Count of partitions with new events (high_watermark > position).
- **active_consumers**: Count of consumers with valid heartbeats.
- **active_consumers_weight**: Sum of weights of all active consumers in the subscription.
- **last_modified_at**: Timestamp of the last statistics update.

These statistics are used for:

- **Adaptive Heartbeat Intervals**: The heartbeat interval is adjusted based on the number of active consumers to maintain a target QPS (queries per second) for the cluster.

Precomputing these statistics reduces the need for expensive queries during consumer check-ins.

## Domain Classes

The Boxy Core module uses Java records to model the schema. Relevant classes:

```mermaid
classDiagram
    class Consumer {
        +String id
        +long subscriptionId
        +double weight
        +Instant heartbeatDetectedAt
        +double heartbeatInterval
        +Instant heartbeatDeadline
        +List~Long~ topicIds
    }
    class Cursor {
        +long id
        +long subscriptionId
        +long partitionId
        +int randomKey
        +long position
    }
```

## Heartbeats

Consumer-level heartbeat (in the consumers table) remains independent of per-partition state.

Rather than every consumer polling for events N-times per seconds, we define:

- T<sub>cycle</sub>: a fixed “heartbeat cycle” (e.g. 1 s)

- QPS<sub>target</sub>: the desired total heartbeats/sec for all active consumers of a subscription (e.g. 10 qps)

On each cycle, each consumer flips a weighted coin with probability `p = min(1, target_QPS / N_active)`, 
and only writes a heartbeat if it “wins” that flip.

Properties
- When N_active ≤ Q_target, then p = 1 → everyone writes → we get N_active QPS (fine for small clusters).
- When N_active > Q_target, then p = Q_target / N_active → expected cluster rate ≈ Q_target, irrespective of N.
- We detect failures in a bounded time: expected per-consumer heartbeat interval = 1 s / p = N_active / Q_target seconds;
  we pick the dead‐timeout to be a small multiple of that (e.g. 3×).

---

## Client Consumer Protocol

The Client Consumer Protocol defines how a client implementation for a given programming language must interact with Boxy. 
All interaction occurs via stored procedures, and each client instance is identified by a `session_id` that must 
be supplied on every call. Each stored procedure call is atomic. Database connections do not need to be reused between calls,  
and transactions may not span multiple client consumer calls.

The protocol specifies how clients register, poll for events, apply backoff when idle, and commit offsets. 
It is designed to support large-scale fan-out with many concurrent clients while strictly limiting the total polling 
and heartbeat queries per subscription. As an implementer, you can treat the protocol as a small, well-defined 
state machine driven by stored procedure calls keyed by `session_id`.

### Consumer Stored Procedures

At a minimum, a client consumer implementation must use the following stored procedures:

#### Registration Procedure

`sp_consumers__register(session_id, subscription_name, topics_json)`
Registers a consumer against a subscription and declares the set of topics it intends to consume.
A single consumer is bound to one subscription name for a given topic (or multi-topic pattern).
A process / service can create multiple consumer instances, each with its own subscription name (even on the same topics),
and thereby “use more than one subscription name” overall.

Parameters:

  * `session_id` is the identifier for the consumer session and is used on every subsequent call.
       It MUST be a high-entropy identifier (UUIDv4 recommended), MUST NOT be reused across registrations,
       and the server will reject duplicate `session_id` values that are still active. After a crash the client
       may immediately re-register with a new `session_id` using the same `subscription_name` and `topics_json`.
  * `subscription_name` refers to a subscription that already links to one or more topics.
  * `topics_json` is a JSON array of fully qualified topic paths, selecting a subset of the subscription’s topics for this consumer.
       Wildcards/patterns are not supported; unknown topics are rejected with `INVALID_TOPIC`. A consumer may re-register
       with a new `session_id` and different `topics_json` to change its coverage.

#### Polling Procedure

`sp_events__poll(session_id)`
Polling procedure that returns the next events plus polling frequency guidance.
When a client `poll`s after registration, it will receive events starting from the `last committed position`
for the topics it has registered. Subsequent calls to `poll` will receive events from the `last read position` for
the topics it has registered.

The server returns zero or more events plus metadata that instructs the client on polling frequency.
If events are received, the client MAY invoke `sp_events__poll` again immediately or with a short delay,
ignoring guidance on polling frequency; if no events are received,
the client MUST use the returned metadata to determine the next polling time.
  
Parameters:
* `session_id` identify the session.

The procedure returns two result sets. The first result set will be the events, the second result set will be the metadata with the following columns:

* `polling_probability` (`DOUBLE`): probability **per millisecond** that a client should poll. Clients compute
  the probability of polling based on elapsed time since the last poll, e.g. `p = 1 - (1 - polling_probability)^(elapsed_ms)`;
  draw a random number and poll when the threshold is crossed. Clamp polling to a minimum of one poll per 10 seconds
  to avoid starvation.

#### Commit Procedure

`sp_cursors__commit(session_id, cursor_positions_json)`
Records the consumer’s progress for one or more topics after successful processing of events.
Each commit advances the stored position for the corresponding cursors, so that subsequent sessions
continue reading from the `last committed position`.

An implementation can decide when to call `commit`. It is not necessary to call `commit` after every
call to `poll`. For maximum throughput, it is recommended an implementation call `commit` periodically
(e.g., every 1-second) and/or after it processes a certain number of events. Commits are idempotent per position and
MUST be monotonic per `cursor_id`; stale or decreasing positions are rejected with `STALE_COMMIT`.

An implementation SHOULD call commit before it deregisters if it has successfully processed any events
since its last commit. Batching commits (e.g., “every N events or every T ms, whichever comes first”) is encouraged
to reduce database writes.

Parameters:
* `session_id` identify the session.
* `cursor_positions_json` a JSON map of one or more `cursor_id` and `position` entries,
  with the `cursor_id` as the map key, and `position` as the map value.

#### Deregistration Procedure

`sp_consumers__deregister(session_id)`
SHOULD be called when a client is shutting down.
Before deregistering, an implementation SHOULD complete processing of inflight events and commit cursor positions.
If an implementation becomes inactive without calling `deregister` other consumers will be blocked until deadlines pass
(`heartbeat_deadline` ≈ 3 × expected heartbeat interval). After deregistering, an implementation MUST NOT make further
calls with the same `session_id`.

Parameters:
* `session_id` identify the session.

### Lifecycle and state names

- **Unregistered** → transient phase before registration completes.
- **Registered** → successfully registered but no poll yet issued.
- **Receiving** → polling and receiving event batches without delay.
- **Backoff** → idle/heartbeat mode driven by server-provided probability.
- **Deregistering** → deregistering and shutting down.

State transitions (client side)

| State         | Allowed actions                                         | Transition trigger                                        |
|---------------|---------------------------------------------------------| --------------------------------------------------------- |
| Unregistered  | `register`                                              | Success → Registered; error → abort                       |
| Registered    | `poll`                                                  | First `poll` → Receiving or Backoff (if empty batch)       |
| Receiving     | `poll`, `commit`, `deregister`                          | Empty batch → Backoff; graceful shutdown → Deregistering   |
| Backoff       | `poll` per `polling_probability` `commit`, `deregister`, | Events returned → Receiving; timeout → deregister/shutdown |
| Deregistering | -                                                       | `deregister` success → terminal                            |

### Error handling and observability

| Procedure  | Example error codes/messages         | Recommended client action                          |
| ---------- |--------------------------------------| -------------------------------------------------- |
| register   | `INVALID_TOPIC`, `DUPLICATE_SESSION` | Fail fast; regenerate `session_id` and retry       |
| poll       | `UNKNOWN_SESSION`                    | Re-register if unknown; serialize polls and retry  |
| commit     | `UNKNOWN_SESSION`, `STALE_COMMIT`    | Re-register if unknown; drop or refresh cursors    |
| deregister | `UNKNOWN_SESSION`                    | Ignore; session already expired                    |

  
## Building the Project

Boxy uses Maven and requires Java 17+. From the root:

```bash
mvn clean package
```

Integration tests use Testcontainers with MySQL (default) or Postgres. Configure via env vars:

| Variable      | Default       | Description           |
| ------------- | ------------- | --------------------- |
| `DB_TYPE`     | `mysql`       | `mysql` or `postgres` |
| `DB_HOST`     | `localhost`   | Database host         |
| `DB_PORT`     | `3306`/`5432` | Database port         |
| `DB_NAME`     | `events_db`   | Schema name           |
| `DB_USER`     | `user`        | Database user         |
| `DB_PASSWORD` | `password`    | Database password     |

## Getting Started

### Prerequisites

1. Java 17 or higher
2. MySQL 8.0+ or PostgreSQL 12+
3. Maven 3.6+

### Database Setup

1. Create a database for Boxy:

```sql
CREATE DATABASE events_db;
CREATE USER 'user'@'localhost' IDENTIFIED BY 'password';
GRANT ALL PRIVILEGES ON events_db.* TO 'user'@'localhost';
```

2. Run the Liquibase migrations to set up the schema:

```bash
mvn liquibase:update -Dliquibase.url=jdbc:mysql://localhost:3306/events_db -Dliquibase.username=user -Dliquibase.password=password
```


## FAQ

### If my application models multi-tenancy using a database schema per tenant, should I install boxy in each tenant?

If your application models multi-tenancy by placing each tenant in its own database schema, 
you should still use a single boxy schema. Boxy is designed to be multi-tenant and support hierarchical namespaces, 
so you should use the top-level namespace to designate the tenant.


### How is the schema managed?
Liquibase is used for schema management, but we do not use the database agnostic schema definitions. 
When this was attempted it was found that 1) the resulting YAML files were overly complex and required too many 
exceptions, and 2) the generated schemas would not perform as well as hand-crafted schemas without 
additional exceptions. Since we aim to support a wide range of databases, a decision was made to maintain complete 
control over the schema.

## Contributing

Contributions to Boxy are welcome! Here's how you can contribute:

1. **Fork the Repository**: Start by forking the repository on GitHub.

2. **Create a Branch**: Create a branch for your feature or bugfix.
   ```bash
   git checkout -b feature/your-feature-name
   ```

3. **Make Changes**: Implement your changes, following the existing code style.

4. **Write Tests**: Add tests for your changes to ensure they work correctly.

5. **Run Tests**: Make sure all tests pass before submitting your changes.
   ```bash
   mvn test
   ```

6. **Submit a Pull Request**: Push your changes to your fork and submit a pull request to the main repository.

## License

Boxy is released under the Apache License 2.0. See the [LICENSE](LICENSE) file for details.
See the License for the specific language governing permissions and
limitations under the License.

