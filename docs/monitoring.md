# Boxy Monitoring Guide

This document describes the observability features built into Boxy and provides
recommendations for production monitoring with Prometheus and Grafana.

## Micrometer Metrics Integration

Boxy publishes metrics via [Micrometer](https://micrometer.io/), a vendor-neutral
metrics façade. By default it uses a `SimpleMeterRegistry` (in-process, no export).

### Configuring a Prometheus Registry

```java
PrometheusMeterRegistry prometheus = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
BoxyMeterRegistry.set(prometheus);

// Expose the scrape endpoint (e.g. using Jetty or Vert.x):
// GET /metrics → prometheus.scrape()
```

Add `micrometer-registry-prometheus` to your project:

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
    <version>1.14.5</version>
</dependency>
```

### Available Metrics

| Metric Name | Type | Description | Alert Threshold |
|-------------|------|-------------|----------------|
| `boxy.publish.latency` | Timer | Latency of `sp_events__publish` calls | p99 > 50ms |
| `boxy.publish.batch.latency` | Timer | Latency of `sp_events__publish_multi` batch calls | p99 > 200ms |
| `boxy.poll.latency` | Timer | Latency of `sp_events__poll` calls | p99 > 100ms |
| `boxy.commit.latency` | Timer | Latency of `sp_cursors__commit` calls | p99 > 50ms |
| `boxy.sequencer.lag` | Gauge | Depth of `unprocessed_events` table | > 10,000 for > 60s |
| `boxy.consumer.gc.reaped` | Counter | Consumers removed by GC | — |
| `hikaricp.connections` | Gauge | Total HikariCP pool connections | — |
| `hikaricp.connections.active` | Gauge | Active (in-use) connections | > 80% of pool size |
| `hikaricp.connections.idle` | Gauge | Idle connections waiting | — |
| `hikaricp.connections.pending` | Gauge | Threads waiting for a connection | > 0 for > 5s |

### HikariCP Pool Metrics

HikariCP automatically registers pool metrics with the Micrometer registry when
`BoxyMeterRegistry.set(registry)` is called before the first connection pool creation.

All HikariCP metrics carry a `pool` tag set to `boxy-cp`.

### Sequencer Lag (Item #81)

The sequencer lag metric (`boxy.sequencer.lag`) is a Gauge that tracks the number
of events in `unprocessed_events` that have not yet been sequenced. Register it once
at startup using the `DataSource`:

```java
Gauge.builder(BoxyMeterRegistry.SEQUENCER_LAG, dataSource, ds -> {
    try (var conn = ds.getConnection();
         var ps = conn.prepareStatement("SELECT COUNT(*) FROM unprocessed_events");
         var rs = ps.executeQuery()) {
        return rs.next() ? rs.getLong(1) : 0L;
    } catch (SQLException e) {
        return -1L;
    }
})
.description("Number of unprocessed events waiting to be sequenced")
.register(BoxyMeterRegistry.get());
```

## Logging Configuration

Boxy uses [SLF4J](https://www.slf4j.org/) for logging with Logback as the default
implementation in `boxy-core`.

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `BOXY_LOG_LEVEL` | `INFO` | Root log level (`TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`) |
| `BOXY_LOG_FORMAT` | `text` | Log format: `text` (human-readable) or `json` (structured) |
| `BOXY_HIKARI_LOG_LEVEL` | `WARN` | Log level for HikariCP (verbose at `DEBUG`) |
| `BOXY_SQL_LOG_LEVEL` | `INFO` | Log level for SQL tracing in `boxy.core.repository` |

### SQL Tracing

To trace all SQL executions (useful for debugging):

```bash
export BOXY_SQL_LOG_LEVEL=DEBUG
```

Or in code:
```java
LoggerFactory.getLogger("boxy.core.repository")  // set to DEBUG via your logging config
```

Every call to `BaseRepository.execute()` logs:
- **Before**: SQL text and parameter count (DEBUG)
- **After**: SQL text and completion marker (DEBUG)
- **On error**: SQL text, SQL state, vendor error code, and message (WARN)

### JSON Log Format

For production environments with centralized log aggregation, set `BOXY_LOG_FORMAT=json`.
Output will be one JSON object per line:

```json
{"ts":"2026-03-02T12:34:56.789Z","level":"DEBUG","thread":"main","logger":"boxy.core.repository.BaseRepository","msg":"Executing SQL: sql='{CALL sp_events__publish(?,?,?,?)}' params=4"}
```

For richer JSON logging (MDC, stack traces, nested fields), replace Logback's built-in
encoder with [logstash-logback-encoder](https://github.com/logfellow/logstash-logback-encoder).

## Health Check

Use `HealthCheck.check(dataSource)` to verify database connectivity and report the
sequencer backlog:

```java
HealthCheck.Result result = HealthCheck.check(DataSourceProvider.dataSource());
if (!result.healthy()) {
    log.error("Boxy health check failed: {}", result.details());
}
log.info("Sequencer backlog: {} events", result.backlogDepth());
```

The health check:
1. Executes `SELECT 1` to confirm pool connectivity
2. Queries `SELECT COUNT(*) FROM unprocessed_events` to report sequencer backlog depth

A persistently non-zero and growing `backlogDepth` indicates the MySQL event scheduler
may have stopped. See the [Configuration Guide](configuration.md) for scheduler setup.

## Recommended Grafana Dashboards

### Dashboard 1: Boxy Pipeline Overview

Panels:
- **Publish throughput** — `rate(boxy_publish_latency_count[1m])` events/sec
- **Publish p99 latency** — `histogram_quantile(0.99, rate(boxy_publish_latency_bucket[5m]))`
- **Poll throughput** — `rate(boxy_poll_latency_count[1m])` polls/sec
- **Poll p99 latency** — `histogram_quantile(0.99, rate(boxy_poll_latency_bucket[5m]))`
- **Commit throughput** — `rate(boxy_commit_latency_count[1m])` commits/sec
- **Sequencer lag** — `boxy_sequencer_lag` (alert if > 10,000 for 60s)

### Dashboard 2: Connection Pool Health

Panels:
- **Active connections** — `hikaricp_connections_active{pool="boxy-cp"}`
- **Pending threads** — `hikaricp_connections_pending{pool="boxy-cp"}`
- **Pool utilisation** — `hikaricp_connections_active / hikaricp_connections`

### Alerting Thresholds

| Alert | Query | Threshold | Severity |
|-------|-------|-----------|----------|
| High publish latency | `histogram_quantile(0.99, rate(boxy_publish_latency_bucket[5m]))` | > 0.05s | Warning |
| High poll latency | `histogram_quantile(0.99, rate(boxy_poll_latency_bucket[5m]))` | > 0.1s | Warning |
| Sequencer backlog growing | `deriv(boxy_sequencer_lag[5m]) > 0 and boxy_sequencer_lag > 10000` | — | Critical |
| Connection pool saturation | `hikaricp_connections_pending{pool="boxy-cp"} > 0` | 5 min sustained | Warning |
