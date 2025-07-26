package boxy.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Command for database operations.
 * This class defines the 'db' subcommand and its subcommands.
 */
@Command(
        name = "db",
        description = "Database operations",
        mixinStandardHelpOptions = true,
        subcommands = {
                DbInitCommand.class,
                DbMigrateCommand.class
        }
)
public class DbCommand {

    @Option(names = {"-v", "--verbose"}, description = "Enable verbose output")
    private boolean verbose;

}