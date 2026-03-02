package boxy.cli.cmd;

import boxy.cli.ConnectionOptions;
import boxy.mysql.repository.ConsumerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@code boxy event listen <subscription> <topic-path> [more topics...]}
 *
 * <p>Registers a temporary consumer, polls for events in a loop, and prints each event to
 * stdout as JSON. Pressing Ctrl+C triggers graceful deregistration before exit.
 *
 * <p>Example:
 * <pre>{@code
 * boxy event listen my-sub tenant-a/payments/orders tenant-a/payments/refunds
 * }</pre>
 */
@Command(
        name = "listen",
        description = "Register as a consumer and listen for events on one or more topic paths.",
        mixinStandardHelpOptions = true
)
public class EventListenCommand implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(EventListenCommand.class);

    @Mixin
    private ConnectionOptions conn;

    @Parameters(index = "0", description = "Subscription name to register under")
    private String subscriptionName;

    @Parameters(index = "1..*", description = "One or more fully-qualified topic paths (namespace/topic)")
    private List<String> topicPaths;

    @Option(names = {"--poll-interval-ms"}, description = "Milliseconds to sleep between polls when idle (default: 100)", defaultValue = "100")
    private long pollIntervalMs;

    @Option(names = {"--format"}, description = "Output format: json (default) or text", defaultValue = "json")
    private String format;

    @Override
    public Integer call() {
        final var consumerId = UUID.randomUUID().toString();
        final var ds = conn.dataSource();
        final var consumerRepo = new ConsumerRepository(ds);
        final var running = new AtomicBoolean(true);

        // Graceful shutdown on Ctrl+C
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            running.set(false);
            try {
                consumerRepo.deregister(consumerId);
                log.info("Consumer deregistered: {}", consumerId);
            } catch (Exception e) {
                log.warn("Deregistration failed (may already be expired): {}", e.getMessage());
            }
        }, "boxy-cli-shutdown"));

        log.info("Registering consumer id={} subscription={} topics={}", consumerId, subscriptionName, topicPaths);
        try {
            consumerRepo.register(consumerId, subscriptionName, topicPaths);
            System.err.printf("[boxy] Listening as consumer %s. Press Ctrl+C to stop.%n", consumerId);

            while (running.get()) {
                // Call sp_events__poll directly — it returns two result sets:
                // RS 1: events (cursor_id, partition_id, sequence, event_id, data)
                // RS 2: metadata (polling_probability)
                final var events = new ArrayList<long[]>();
                final var dataList = new ArrayList<String>();
                try (final var c = ds.getConnection();
                     final var stmt = c.prepareCall("{CALL sp_events__poll(?)}")) {
                    stmt.setString(1, consumerId);
                    if (stmt.execute()) {
                        try (final ResultSet rs = stmt.getResultSet()) {
                            while (rs.next()) {
                                events.add(new long[]{rs.getLong("event_id"), rs.getLong("partition_id")});
                                dataList.add(rs.getString("data"));
                            }
                        }
                    }
                }
                if (events.isEmpty()) {
                    Thread.sleep(pollIntervalMs);
                    continue;
                }
                for (int i = 0; i < events.size(); i++) {
                    printEvent(events.get(i)[0], events.get(i)[1], dataList.get(i));
                }
            }
            return 0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 0;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            log.error("Listen failed", e);
            return 1;
        }
    }

    private void printEvent(final long eventId, final long partitionId, final String data) {
        if ("json".equalsIgnoreCase(format)) {
            System.out.printf("{\"eventId\":%d,\"partitionId\":%d,\"data\":%s}%n",
                    eventId, partitionId, data);
        } else {
            System.out.printf("event_id=%d partition_id=%d data=%s%n",
                    eventId, partitionId, data);
        }
    }
}
