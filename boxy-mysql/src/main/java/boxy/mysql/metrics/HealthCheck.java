package boxy.mysql.metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.SQLException;

/**
 * Lightweight health-check utility for Boxy database connectivity and sequencer liveness.
 *
 * <p>Provides two independent checks:
 * <ol>
 *   <li><b>Database connectivity</b> — executes {@code SELECT 1} via the given
 *       {@link DataSource} to confirm the pool can acquire a connection and the
 *       database server is responsive.</li>
 *   <li><b>Sequencer liveness</b> — queries the {@code unprocessed_events} table to
 *       report the current backlog depth.  A depth that is not growing over time
 *       indicates the MySQL event scheduler is running normally.</li>
 * </ol>
 *
 * <p>Usage example (e.g. in a readiness probe endpoint):
 * <pre>{@code
 * HealthCheck.Result result = HealthCheck.check(dataSource);
 * if (!result.healthy()) {
 *     return Response.serverError().entity(result.details()).build();
 * }
 * }</pre>
 *
 * <p>Item #87 — health check endpoint / utility for database connectivity
 *   and sequencer liveness.
 */
public final class HealthCheck {

    private static final Logger log = LoggerFactory.getLogger(HealthCheck.class);

    private HealthCheck() {}

    /**
     * Immutable result of a {@link HealthCheck#check(DataSource)} call.
     *
     * @param healthy    {@code true} if all checks passed
     * @param details    human-readable summary of the check
     * @param backlogDepth number of unprocessed events at the time of check;
     *                     {@code -1} if the query failed
     */
    public record Result(boolean healthy, String details, long backlogDepth) {}

    /**
     * Runs all health checks against the given data source.
     *
     * @param ds the data source to check; must not be {@code null}
     * @return a {@link Result} describing the health state
     */
    public static Result check(final DataSource ds) {
        // Check 1: database connectivity
        try (final var conn = ds.getConnection();
             final var ps   = conn.prepareStatement("SELECT 1");
             final var rs   = ps.executeQuery()) {
            if (!rs.next() || rs.getInt(1) != 1) {
                return new Result(false, "Database connectivity check returned unexpected result", -1L);
            }
        } catch (SQLException e) {
            log.warn("Health check: database connectivity failed — {}", e.getMessage());
            return new Result(false, "Database connectivity failed: " + e.getMessage(), -1L);
        }

        // Check 2: sequencer liveness (unprocessed_events backlog depth)
        final long backlog;
        try (final var conn = ds.getConnection();
             final var ps   = conn.prepareStatement("SELECT COUNT(*) FROM unprocessed_events");
             final var rs   = ps.executeQuery()) {
            backlog = rs.next() ? rs.getLong(1) : -1L;
        } catch (SQLException e) {
            log.warn("Health check: sequencer liveness query failed — {}", e.getMessage());
            return new Result(false, "Sequencer liveness query failed: " + e.getMessage(), -1L);
        }

        final String details = "Database OK. Sequencer backlog: " + backlog + " unprocessed events.";
        log.debug("Health check passed — {}", details);
        return new Result(true, details, backlog);
    }
}
