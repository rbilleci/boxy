# Evaluation: Async JDBC Drivers for Boxy

**Item 127/164** — Evaluate whether to use async JDBC drivers (e.g., MySQL Connector/J with async support, R2DBC) versus blocking JDBC for higher throughput.

## Current State

Boxy currently uses **blocking JDBC** (mysql-connector-j 8.4.0):
- Synchronous `Connection.prepareCall()` and `ResultSet` iteration
- One thread per concurrent consumer group poll
- Thread pool sized to concurrency (e.g., 4 threads for 4 partition leases)
- Works well up to moderate concurrency; scales sublinearly beyond ~100 concurrent operations

**Performance characteristics**:
- Thread overhead: ~1-2 MB stack per thread
- Context switching: expensive with 1000+ threads
- Database connection overhead: one JDBC connection per thread

## Options Evaluated

### Option A: Continue with Blocking JDBC (Current)

**Pros**:
- Simple, well-understood programming model
- JDK libraries support (`java.sql.*`)
- Great debugging and profiling tools
- Predictable latency (no event loop contention)
- Works fine for <100 concurrent operations

**Cons**:
- Poor thread utilization (one thread blocks on I/O)
- Memory overhead (threads are heavy)
- Scales poorly beyond ~100 concurrent consumers
- Context switch overhead with 1000+ threads

**Throughput estimate**:
- With 100 threads: ~50-100K events/sec (I/O bound, not CPU)
- Beyond 100 threads: diminishing returns due to GC and context switching

### Option B: R2DBC (Reactive JDBC)

[R2DBC](https://r2dbc.io/) is a reactive, non-blocking driver for databases.

**Pros**:
- Non-blocking, event-loop style (Netty-based)
- Supports thousands of concurrent operations with few threads
- Lower memory overhead (no thread stack per connection)
- Growing ecosystem (Spring Data R2DBC, Liquibase support)
- Suitable for 1M events/sec goal

**Cons**:
- Smaller ecosystem than traditional JDBC
- Not all MySQL features supported yet (some edge cases)
- Steeper learning curve (reactive programming)
- Liquibase may require custom integration
- Less mature than blocking JDBC

**Driver**: `io.r2dbc:r2dbc-mysql` (MySQL connector for R2DBC)

**Throughput estimate**:
- 50-100 concurrent operations: Similar to blocking
- 1000+ concurrent operations: 200-500K events/sec (2-5x improvement)

### Option C: MySQL Connector/J with Virtual Threads (JDK 21+)

JDK 21 introduced virtual threads (lightweight threads). MySQL Connector/J 8.4+ supports virtual threads via `com.mysql.cj.protocol.Locking.virtualThreads`.

**Pros**:
- Minimal code changes (drop-in replacement)
- Scales to millions of concurrent operations
- Works with existing JDBC code
- Minimal mental overhead (traditional blocking style)

**Cons**:
- Requires JDK 21+ (project targets JDK 25, so OK)
- Virtual threads still have small overhead per thread
- Liquibase integration straightforward
- Performance depends on database load

**Throughput estimate**:
- 100-1000 concurrent operations: 100-300K events/sec
- 10,000 virtual threads: 500K-1M events/sec (dependent on DB)

## Recommendation

**Option C: Virtual Threads (JDK 21+)**

### Rationale

1. **Scalability**: Virtual threads can scale to 10,000+ concurrent operations with minimal overhead, suitable for 1M events/sec goal.
2. **Maintainability**: Minimal code changes; existing JDBC code continues to work.
3. **Project target**: Boxy targets JDK 25 (virtual threads are stable as of JDK 25).
4. **Integration**: Works seamlessly with Liquibase and existing tooling.
5. **Risk**: Lower risk than a major rewrite to R2DBC.

### Implementation Plan

1. **Enable virtual threads** in MySQL Connector/J:
   ```properties
   # database.properties
   com.mysql.cj.protocol.Locking.virtualThreads=true
   ```

2. **Update connection pooling** to use virtual thread-aware pool (HikariCP 5.0+):
   ```java
   HikariConfig config = new HikariConfig();
   config.setJdbcUrl("jdbc:mysql://...");
   config.setMaximumPoolSize(10);  // Reduced from 100 due to virtual threads
   config.setMinimumIdle(5);
   ```

3. **Benchmark** with virtual threads enabled:
   ```bash
   mvn clean verify -Pbench-cloud-prod
   ```

4. **Monitor** virtual thread creation:
   ```bash
   # Add JVM arg to log virtual thread events
   -XX:+UnlockDiagnosticVMOptions
   -XX:+TraceVirtualThreads
   ```

### Fallback

If virtual thread performance is unsatisfactory or new blocking issues emerge, migrate to R2DBC (Option B) for full async/reactive support. However, this would require significant refactoring.

### Metrics to Track

- **Throughput**: Events/sec (target: 1M for sustained workload)
- **Latency**: p50, p95, p99 percentiles (should remain <10ms)
- **GC pressure**: Full GC frequency (should be rare)
- **Memory**: Heap usage (should scale linearly with events, not concurrency)
- **Thread count**: Should remain ~10-20 (HikariCP + virtual threads)

## References

- [JDK 21 Virtual Threads](https://openjdk.org/jeps/444)
- [MySQL Connector/J Virtual Threads Support](https://dev.mysql.com/doc/connector-j/8.4/en/connector-j-connection-pooling.html)
- [R2DBC Documentation](https://r2dbc.io/)
- [HikariCP Virtual Thread Support](https://github.com/brettwooldridge/HikariCP/issues/1830)

## Timeline

- **Milestone**: Post-release (1.1 or later)
- **Effort**: Low (1-2 days for implementation and testing)
- **Risk**: Low (virtual threads are stable)
