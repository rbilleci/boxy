# ADR 003: Cursor Lease Model for Consumer Ownership

## Status
Accepted

## Context

Boxy allows multiple consumer instances to share partition reading. The system must:
1. Prevent duplicate delivery: Each event read by exactly one consumer
2. Enable failover: Crashed consumers' partitions are re-acquired
3. Support rebalancing: Adding/removing consumers shifts load

**Options**:
1. **Consumer groups** (Kafka-style): Group coordination with leader
2. **Lease model**: Explicit time-limited leases per cursor
3. **External coordination**: Zookeeper/etcd for assignment

**Constraints**:
- No external dependencies
- High availability without central coordinator
- MySQL 8.0+ available

## Decision

Use **cursor lease model**:

1. Consumers **explicitly register** via `sp_consumers__register()`
2. On **poll**, acquire time-limited leases on cursors
3. Leases auto-expire after `lease.lock.seconds`
4. **Expired leases** released automatically
5. **On commit**, leases immediately released

## Consequences

### Positive

1. **No external coordination**: Coordination via MySQL transactions
2. **Simple failover**: Dead consumer leases expire automatically
3. **Automatic rebalancing**: New consumers acquire leases immediately
4. **Fast commit**: Releasing leases on commit speeds handoff
5. **Tunable**: Duration configurable via boxy_config

### Negative

1. **Lease expiry latency**: Takes 3s for others to pick up partition
2. **Thundering herd**: All consumers race on lease expiry
3. **Lease renewal overhead**: Each poll renews all leases
4. **No group semantics**: No rebalance listeners/callbacks

## Related Decisions

- [ADR 001](001-mysql-stored-procedure-protocol.md) — Leases in `sp_events__poll()`
- [ADR 005](005-liquibase-versioned-sps.md) — Lease model evolved across versions
