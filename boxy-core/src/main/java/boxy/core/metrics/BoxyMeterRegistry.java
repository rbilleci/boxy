package boxy.core.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Application-scoped Micrometer {@link MeterRegistry} holder for Boxy.
 *
 * <p>Defaults to a {@link SimpleMeterRegistry} so that the library works
 * out of the box without any external metrics infrastructure.  Production
 * deployments should call {@link #set(MeterRegistry)} early in application
 * startup to replace it with a registry connected to Prometheus, DataDog,
 * or another backend.
 *
 * <p>Example — Prometheus setup:
 * <pre>{@code
 * PrometheusMeterRegistry prometheus = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
 * BoxyMeterRegistry.set(prometheus);
 * // later, expose prometheus.scrape() via HTTP
 * }</pre>
 *
 * <p>Metric naming follows the Micrometer convention: {@code boxy.<area>.<operation>}.
 * All timing metrics use seconds as the base unit (Micrometer default).
 *
 * <p>Registered metrics:
 * <ul>
 *   <li>{@code boxy.publish.latency} — time per sp_events__publish call</li>
 *   <li>{@code boxy.publish.batch.latency} — time per sp_events__publish_multi call</li>
 *   <li>{@code boxy.poll.latency} — time per sp_events__poll call</li>
 *   <li>{@code boxy.commit.latency} — time per sp_cursors__commit call</li>
 *   <li>{@code boxy.sequencer.lag} — current depth of unprocessed_events table (Gauge)</li>
 *   <li>{@code boxy.consumer.gc.reaped} — consumers removed in last GC run (Counter)</li>
 *   <li>{@code boxy.db.pool.*} — HikariCP connection pool metrics (auto-registered via HikariCP)</li>
 * </ul>
 */
public final class BoxyMeterRegistry {

    // Metric name constants
    public static final String PUBLISH_LATENCY       = "boxy.publish.latency";
    public static final String PUBLISH_BATCH_LATENCY = "boxy.publish.batch.latency";
    public static final String POLL_LATENCY          = "boxy.poll.latency";
    public static final String COMMIT_LATENCY        = "boxy.commit.latency";
    public static final String SEQUENCER_LAG         = "boxy.sequencer.lag";
    public static final String CONSUMER_GC_REAPED    = "boxy.consumer.gc.reaped";

    private static volatile MeterRegistry registry = new SimpleMeterRegistry();

    private BoxyMeterRegistry() {}

    /**
     * Returns the current application-scoped registry.
     * Defaults to {@link SimpleMeterRegistry} if never configured.
     *
     * @return the current {@link MeterRegistry}; never {@code null}
     */
    public static MeterRegistry get() {
        return registry;
    }

    /**
     * Replaces the application-scoped registry.
     * Should be called once during application startup before any Boxy operations begin.
     *
     * @param newRegistry the new registry; must not be {@code null}
     * @throws NullPointerException if {@code newRegistry} is {@code null}
     */
    public static void set(final MeterRegistry newRegistry) {
        if (newRegistry == null) throw new NullPointerException("newRegistry must not be null");
        registry = newRegistry;
    }
}
