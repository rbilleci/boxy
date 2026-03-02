package boxy.cli;

import boxy.cli.cmd.DbCommand;
import boxy.cli.cmd.EventCommand;
import boxy.cli.cmd.NamespaceCommand;
import boxy.cli.cmd.SubscriptionCommand;
import boxy.cli.cmd.TopicCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Boxy CLI entry point.
 *
 * <p>Usage: {@code boxy [COMMAND]}
 *
 * <p>Provides commands for schema management ({@code db}), event publishing and listening
 * ({@code event}), and management of namespaces, topics, and subscriptions.
 *
 * <p>Connection parameters are resolved from environment variables (DB_TYPE, DB_HOST, DB_PORT,
 * DB_NAME, DB_USER, DB_PASSWORD) with command-line flag overrides available on each subcommand.
 *
 * <p>Build a native binary on macOS ARM64:
 * <pre>{@code mvn package -pl boxy-cli -P native-macos-arm64}</pre>
 */
@Command(
        name = "boxy",
        mixinStandardHelpOptions = true,
        version = "boxy 1.0-SNAPSHOT",
        description = "Boxy — event streaming over a relational database.",
        subcommands = {
                DbCommand.class,
                EventCommand.class,
                NamespaceCommand.class,
                TopicCommand.class,
                SubscriptionCommand.class,
                CommandLine.HelpCommand.class
        }
)
public class Boxy implements Runnable {

    /** Entry point for both JVM and GraalVM native-image execution. */
    public static void main(final String[] args) {
        final int exitCode = new CommandLine(new Boxy()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        // No subcommand given — print usage
        new CommandLine(this).usage(System.out);
    }
}
