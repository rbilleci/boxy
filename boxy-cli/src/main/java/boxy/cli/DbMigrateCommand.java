package boxy.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import javax.sql.DataSource;
import java.util.concurrent.Callable;

/**
 * Command for migrating the database schema.
 * This class implements the 'db migrate' command.
 */
@Command(
        name = "migrate",
        description = "Migrate the database schema to the latest version",
        mixinStandardHelpOptions = true
)
public class DbMigrateCommand implements Callable<Integer> {

    @ParentCommand
    private DbCommand parent;

    @Option(names = {"-u", "--url"}, description = "Database URL (e.g., jdbc:mysql://localhost:3306/boxy)", required = true)
    private String url;

    @Option(names = {"--username"}, description = "Database username", required = true)
    private String username;

    @Option(names = {"--password"}, description = "Database password", required = true)
    private String password;

    @Option(names = {"-v", "--verbose"}, description = "Enable verbose output")
    private boolean verbose;

    /**
     * Executes the 'db migrate' command.
     *
     * @return Exit code
     */
    @Override
    public Integer call() {
        try {
            if (verbose) {
                System.out.println("Migrating database schema...");
                System.out.println("URL: " + url);
                System.out.println("Username: " + username);
            }

            DataSource dataSource = DatabaseUtils.createDataSource(url, username, password);
            boolean success = DatabaseUtils.migrateDatabase(dataSource, verbose);

            if (success) {
                System.out.println("Database schema migrated successfully.");
                return 0;
            } else {
                System.err.println("Failed to migrate database schema.");
                return 1;
            }
        } catch (Exception e) {
            System.err.println("Error migrating database schema: " + e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
            return 1;
        }
    }
}