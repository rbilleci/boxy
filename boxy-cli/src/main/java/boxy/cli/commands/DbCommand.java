package boxy.cli.commands;

import boxy.cli.commands.db.InitCommand;
import boxy.cli.commands.db.MigrateCommand;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

/**
 * Command for database operations.
 * <p>
 * This command handles all database-related operations like initialization and migration.
 * It provides common options for database connection that are shared across all database commands.
 */
@Command(
        name = "db",
        description = "Database operations",
        mixinStandardHelpOptions = true,
        subcommands = {
                InitCommand.class,
                MigrateCommand.class
        }
)
public class DbCommand implements Callable<Integer> {

    @Option(names = {"-u", "--url"}, description = "JDBC URL for the database", required = true)
    private String jdbcUrl;

    @Option(names = {"-n", "--username"}, description = "Database username")
    private String username;

    @Option(names = {"-p", "--password"}, description = "Database password", interactive = true)
    private String password;

    @Option(names = {"--driver"}, description = "JDBC driver class (default: com.mysql.cj.jdbc.Driver)")
    private String driverClass = "com.mysql.cj.jdbc.Driver";

    /**
     * This method is called when the command is executed without any subcommands.
     * It displays the help message.
     *
     * @return Exit code
     */
    @Override
    public Integer call() {
        // If no subcommand is specified, show help for this command
        System.out.println("Please specify a subcommand: init or migrate");
        return 1;
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getDriverClass() {
        return driverClass;
    }
}