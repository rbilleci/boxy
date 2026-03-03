package boxy.cli.cmd;

import boxy.cli.ConnectionOptions;
import boxy.core.repository.EventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.concurrent.Callable;

/**
 * {@code boxy event publish <namespace-path> <topic> --key <key> --data <data>}
 *
 * <p>Publishes a single event to the specified topic. The event is routed to a partition
 * using CRC32 of the key.
 *
 * <p>Example:
 * <pre>{@code
 * boxy event publish tenant-a/payments orders \
 *     --key order-123 \
 *     --data '{"orderId":"123","amount":99.99}'
 * }</pre>
 */
@Command(
        name = "publish",
        description = "Publish a single event to a topic.",
        mixinStandardHelpOptions = true
)
public class EventPublishCommand implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(EventPublishCommand.class);

    @Mixin
    private ConnectionOptions conn;

    @Parameters(index = "0", description = "Namespace path (e.g. tenant-a/payments)")
    private String namespacePath;

    @Parameters(index = "1", description = "Topic name")
    private String topic;

    @Option(names = {"--key", "-k"}, description = "Routing key (used for partition selection via CRC32)", required = true)
    private String key;

    @Option(names = {"--data", "-d"}, description = "Event payload data (any string; typically JSON)", required = true)
    private String data;

    @Override
    public Integer call() {
        log.debug("Publishing event: namespace={} topic={} key={}", namespacePath, topic, key);
        try {
            final var repo = new EventRepository(conn.dataSource());
            repo.publish(namespacePath, topic, key, data);
            System.out.printf("Event published to %s/%s (key=%s)%n", namespacePath, topic, key);
            return 0;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            log.error("Publish failed", e);
            return 1;
        }
    }
}
