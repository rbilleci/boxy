package boxy.cli.cmd;

import boxy.cli.ConnectionOptions;
import boxy.core.repository.TopicRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.concurrent.Callable;

/**
 * Command group: {@code boxy topic}.
 */
@Command(
        name = "topic",
        description = "Topic management commands.",
        subcommands = {
                TopicCommand.CreateCommand.class,
                TopicCommand.DeleteCommand.class,
                HelpCommand.class
        },
        mixinStandardHelpOptions = true
)
public class TopicCommand implements Runnable {

    @Override
    public void run() {
        System.out.println("Use 'boxy topic --help' to see available subcommands.");
    }

    @Command(name = "create", description = "Create a topic in a namespace.", mixinStandardHelpOptions = true)
    static class CreateCommand implements Callable<Integer> {
        private static final Logger log = LoggerFactory.getLogger(CreateCommand.class);

        @Mixin ConnectionOptions conn;

        @Parameters(index = "0", description = "Namespace path")
        private String namespacePath;

        @Parameters(index = "1", description = "Topic name")
        private String topicName;

        @Option(names = {"--partitions", "-p"}, description = "Number of partitions (1–1024, default: 1)", defaultValue = "1")
        private int partitions;

        @Override
        public Integer call() {
            try {
                new TopicRepository(conn.dataSource()).create(namespacePath, topicName, partitions);
                System.out.printf("Topic created: %s/%s (%d partitions)%n", namespacePath, topicName, partitions);
                return 0;
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                log.error("Topic create failed", e);
                return 1;
            }
        }
    }

    @Command(name = "delete", description = "Delete a topic.", mixinStandardHelpOptions = true)
    static class DeleteCommand implements Callable<Integer> {
        private static final Logger log = LoggerFactory.getLogger(DeleteCommand.class);

        @Mixin ConnectionOptions conn;

        @Parameters(index = "0", description = "Namespace path")
        private String namespacePath;

        @Parameters(index = "1", description = "Topic name")
        private String topicName;

        @Override
        public Integer call() {
            try {
                new TopicRepository(conn.dataSource()).delete(namespacePath, topicName);
                System.out.printf("Topic deleted: %s/%s%n", namespacePath, topicName);
                return 0;
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                log.error("Topic delete failed", e);
                return 1;
            }
        }
    }
}
