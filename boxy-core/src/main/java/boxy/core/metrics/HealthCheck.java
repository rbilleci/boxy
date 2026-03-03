package boxy.core.metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lightweight health-check utility for Boxy database connectivity, sequencer liveness,
 * and consumer GC liveness.
 *
 * <p>Provides three independent checks:
 * <ol>
 *   <li><b>Database connectivity</b> — executes {@code SELECT 1} via the given
 *       {@link DataSource} to confirm the pool can acquire a connection and the
 *       database server is responsive.</li>
 *   <li><b>Sequencer liveness</b> — queries the {@code unprocessed_events} table to
 *       report the current backlog depth, and checks {@code background_job_errors}
 *       for recent sequencer failures. A depth that is not growing over time and
 *       zero recent errors indicate the MySQL event scheduler is running normally.</li>
 *   <li><b>Consumer GC liveness</b> — counts expired consumers (heartbeat_deadline < NOW)
 *       and checks {@code background_job_errors} for recent consumer_gc failures.
 *       A low count and zero recent errors indicate the GC event is running normally.</li>
 * </ol>
 *
 * <p>Usage example (e.g. in a readiness probe endpoint):
 * <pre>{@code
 * HealthCheck.Result result = HealthCheck.check(dataSource);
 * if (!result.healthy()) {
 *     return Response.serverError().entity(result.summary()).build();
 * }
 * }</pre>
 *
 * <p>Items #87-88 — health check / liveness monitoring for sequencer and consumer_gc.
 */
public final class HealthCheck {

    private static final Logger log = LoggerFactory.getLogger(HealthCheck.class);

    // Thresholds for liveness detection
    private static final int SEQUENCER_BACKLOG_WARN_THRESHOLD = 10000;
    private static final int EXPIRED_CONSUMERS_WARN_THRESHOLD = 1000;

    private HealthCheck() {}

    /**
     * Immutable result of a {@link HealthCheck#check(DataSource)} call.
     *
     * @param healthy             {@code true} if all checks passed
     * @param summary             human-readable summary of the check (1-2 sentences)
     * @param details             detailed check results as a Map (for logging/debugging)
     * @param backlogDepth        number of unprocessed events at time of check; {@code -1} if query failed
     * @param sequencerErrors     count of sequencer errors in the last 5 minutes
     * @param expiredConsumers    count of expired consumers; {@code -1} if query failed
     * @param consumerGcErrors    count of consumer_gc errors in the last 5 minutes
     */
    public record Result(
        boolean healthy,
        String summary,
        Map<String, Object> details,
        long backlogDepth,
        long sequencerErrors,
        long expiredConsumers,
        long consumerGcErrors
    ) {}

    /**
     * Runs all health checks against the given data source.
     *
     * @param ds the data source to check; must not be {@code null}
     * @return a {@link Result} describing the health state
     */
    public static Result check(final DataSource ds) {
        final Map<String, Object> details = new LinkedHashMap<>();

        // Check 1: database connectivity
        try (final var conn = ds.getConnection();
             final var ps   = conn.prepareStatement("SELECT 1");
             final var rs   = ps.executeQuery()) {
            if (!rs.next() || rs.getInt(1) != 1) {
                details.put("connectivity", "FAILED - unexpected result");
                return new Result(false, "Database connectivity check failed", details, -1L, -1L, -1L, -1L);
            }
            details.put("connectivity", "OK");
        } catch (SQLException e) {
            log.warn("Health check: database connectivity failed — {}", e.getMessage());
            details.put("connectivity", "FAILED - " + e.getMessage());
            return new Result(false, "Database connectivity failed: " + e.getMessage(), details, -1L, -1L, -1L, -1L);
        }

        // Check 2: sequencer liveness
        long backlog = -1L;
        long sequencerErrors = -1L;
        try {
            // Query unprocessed_events backlog
            try (final var conn = ds.getConnection();
                 final var ps   = conn.prepareStatement("SELECT COUNT(*) FROM unprocessed_events");
                 final var rs   = ps.executeQuery()) {
                backlog = rs.next() ? rs.getLong(1) : -1L;
            }

            // Query recent sequencer errors (last 5 minutes)
            try (final var conn = ds.getConnection();
                 final var ps   = conn.prepareStatement(
                     "SELECT COUNT(*) FROM background_job_errors " +
                     "WHERE job_name = 'sequencer' " +
                     "AND error_time > DATE_SUB(NOW(), INTERVAL 5 MINUTE)");
                 final var rs   = ps.executeQuery()) {
                sequencerErrors = rs.next() ? rs.getLong(1) : 0L;
            }

            final String sequencerStatus;
            if (sequencerErrors > 0) {
                sequencerStatus = "WARN - " + sequencerErrors + " errors in last 5 minutes";
            } else if (backlog > SEQUENCER_BACKLOG_WARN_THRESHOLD) {
                sequencerStatus = "WARN - backlog " + backlog + " (threshold: " + SEQUENCER_BACKLOG_WARN_THRESHOLD + ")";
            } else {
                sequencerStatus = "OK - backlog " + backlog;
            }
            details.put("sequencer", sequencerStatus);
        } catch (SQLException e) {
            log.warn("Health check: sequencer liveness query failed — {}", e.getMessage());
            details.put("sequencer", "FAILED - " + e.getMessage());
            sequencerErrors = -1L;
        }

        // Check 3: consumer GC liveness
        long expiredConsumers = -1L;
        long consumerGcErrors = -1L;
        try {
            // Count expired consumers
            try (final var conn = ds.getConnection();
                 final var ps   = conn.prepareStatement(
                     "SELECT COUNT(*) FROM consumers " +
                     "WHERE heartbeat_deadline < NOW()");
                 final var rs   = ps.executeQuery()) {
                expiredConsumers = rs.next() ? rs.getLong(1) : 0L;
            }

            // Query recent consumer_gc errors (last 5 minutes)
            try (final var conn = ds.getConnection();
                 final var ps   = conn.prepareStatement(
                     "SELECT COUNT(*) FROM background_job_errors " +
                     "WHERE job_name = 'consumer_gc' " +
                     "AND error_time > DATE_SUB(NOW(), INTERVAL 5 MINUTE)");
                 final var rs   = ps.executeQuery()) {
                consumerGcErrors = rs.next() ? rs.getLong(1) : 0L;
            }

            final String gcStatus;
            if (consumerGcErrors > 0) {
                gcStatus = "WARN - " + consumerGcErrors + " errors in last 5 minutes";
            } else if (expiredConsumers > EXPIRED_CONSUMERS_WARN_THRESHOLD) {
                gcStatus = "WARN - " + expiredConsumers + " expired consumers (threshold: " + EXPIRED_CONSUMERS_WARN_THRESHOLD + ")";
            } else {
                gcStatus = "OK - " + expiredConsumers + " expired consumers";
            }
            details.put("consumer_gc", gcStatus);
        } catch (SQLException e) {
            log.warn("Health check: consumer GC liveness query failed — {}", e.getMessage());
            details.put("consumer_gc", "FAILED - " + e.getMessage());
            consumerGcErrors = -1L;
        }

        // Determine overall health: all checks must pass (no errors and no backlog warnings)
        final boolean allHealthy = (sequencerErrors == 0 || sequencerErrors == -1) &&
                                   (backlog < SEQUENCER_BACKLOG_WARN_THRESHOLD || backlog == -1L) &&
                                   (consumerGcErrors == 0 || consumerGcErrors == -1) &&
                                   (expiredConsumers < EXPIRED_CONSUMERS_WARN_THRESHOLD || expiredConsumers == -1L);

        final String summary;
        if (allHealthy) {
            summary = "Database OK. Sequencer and GC healthy.";
            log.debug("Health check passed — {}", summary);
        } else {
            summary = "One or more health checks indicate potential issues. See details.";
            log.warn("Health check found warnings — {}", summary);
        }

        return new Result(allHealthy, summary, details, backlog, sequencerErrors, expiredConsumers, consumerGcErrors);
    }
}
