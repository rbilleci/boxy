package boxy.cli.cmd;

import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;

/**
 * Command group: {@code boxy db}.
 *
 * <p>Provides subcommands for managing the Boxy database schema via Liquibase.
 */
@Command(
        name = "db",
        description = "Database schema management commands.",
        subcommands = {
                DbInitCommand.class,
                DbMigrateCommand.class,
                DbStatusCommand.class,
                HelpCommand.class
        },
        mixinStandardHelpOptions = true
)
public class DbCommand implements Runnable {

    @Override
    public void run() {
        System.out.println("Use 'boxy db --help' to see available subcommands.");
    }
}
