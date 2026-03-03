package boxy.cli.cmd;

import boxy.cli.ConnectionOptions;
import boxy.core.repository.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * Command group: {@code boxy subscription}.
 */
@Command(
        name = "subscription",
        description = "Subscription management commands.",
        subcommands = {
                SubscriptionCommand.CreateCommand.class,
                SubscriptionCommand.DeleteCommand.class,
                SubscriptionCommand.SubscribeCommand.class,
                SubscriptionCommand.UnsubscribeCommand.class,
                HelpCommand.class
        },
        mixinStandardHelpOptions = true
)
public class SubscriptionCommand implements Runnable {

    @Override
    public void run() {
        System.out.println("Use 'boxy subscription --help' to see available subcommands.");
    }

    @Command(name = "create", description = "Create a subscription.", mixinStandardHelpOptions = true)
    static class CreateCommand implements Callable<Integer> {
        private static final Logger log = LoggerFactory.getLogger(CreateCommand.class);
        @Mixin ConnectionOptions conn;
        @Parameters(index = "0", description = "Subscription name") private String name;

        @Override
        public Integer call() {
            try {
                new SubscriptionRepository(conn.dataSource()).create(name);
                System.out.println("Subscription created: " + name);
                return 0;
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage()); log.error("Create failed", e); return 1;
            }
        }
    }

    @Command(name = "delete", description = "Delete a subscription.", mixinStandardHelpOptions = true)
    static class DeleteCommand implements Callable<Integer> {
        private static final Logger log = LoggerFactory.getLogger(DeleteCommand.class);
        @Mixin ConnectionOptions conn;
        @Parameters(index = "0", description = "Subscription name") private String name;

        @Override
        public Integer call() {
            try {
                new SubscriptionRepository(conn.dataSource()).delete(name);
                System.out.println("Subscription deleted: " + name);
                return 0;
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage()); log.error("Delete failed", e); return 1;
            }
        }
    }

    @Command(
            name = "subscribe",
            description = "Subscribe to one or more topic paths (format: namespace-path/topic-name).",
            mixinStandardHelpOptions = true
    )
    static class SubscribeCommand implements Callable<Integer> {
        private static final Logger log = LoggerFactory.getLogger(SubscribeCommand.class);
        @Mixin ConnectionOptions conn;
        @Parameters(index = "0", description = "Subscription name") private String name;
        @Parameters(index = "1..*", description = "Topic paths (e.g. tenant-a/payments/orders)") private List<String> topicPaths;

        @Override
        public Integer call() {
            try {
                final var repo = new SubscriptionRepository(conn.dataSource());
                for (final var fullPath : topicPaths) {
                    final int lastSlash = fullPath.lastIndexOf('/');
                    if (lastSlash <= 0) {
                        System.err.println("Error: Topic path must be in format <namespace-path>/<topic-name>, got: " + fullPath);
                        return 1;
                    }
                    final var namespacePath = fullPath.substring(0, lastSlash);
                    final var topicName = fullPath.substring(lastSlash + 1);
                    repo.subscribe(name, namespacePath, topicName);
                    System.out.println("Subscribed " + name + " to " + fullPath);
                }
                return 0;
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage()); log.error("Subscribe failed", e); return 1;
            }
        }
    }

    @Command(
            name = "unsubscribe",
            description = "Remove a topic from a subscription (format: namespace-path/topic-name).",
            mixinStandardHelpOptions = true
    )
    static class UnsubscribeCommand implements Callable<Integer> {
        private static final Logger log = LoggerFactory.getLogger(UnsubscribeCommand.class);
        @Mixin ConnectionOptions conn;
        @Parameters(index = "0", description = "Subscription name") private String name;
        @Parameters(index = "1", description = "Topic path to unsubscribe from (e.g. tenant-a/payments/orders)") private String fullPath;

        @Override
        public Integer call() {
            try {
                final int lastSlash = fullPath.lastIndexOf('/');
                if (lastSlash <= 0) {
                    System.err.println("Error: Topic path must be in format <namespace-path>/<topic-name>, got: " + fullPath);
                    return 1;
                }
                final var namespacePath = fullPath.substring(0, lastSlash);
                final var topicName = fullPath.substring(lastSlash + 1);
                new SubscriptionRepository(conn.dataSource()).unsubscribe(name, namespacePath, topicName);
                System.out.println("Unsubscribed " + name + " from " + fullPath);
                return 0;
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage()); log.error("Unsubscribe failed", e); return 1;
            }
        }
    }
}
