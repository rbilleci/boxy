package boxy.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import javax.sql.DataSource;
import java.util.concurrent.Callable;

/**
 * Command for initializing the database schema.
 * This class implements the 'db init' command.
 */
@Command(
        name = "init",
        description = "Initialize the database schema",
        mixinStandardHelpOptions = true
)
public class DbInitCommand implements Callable<Integer> {

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
     * Executes the 'db init' command.
     *
     * @return Exit code
     */
    @Override
    public Integer call() {
        try {
            if (verbose) {
                System.out.println("Initializing database schema...");
                System.out.println("URL: " + url);
                System.out.println("Username: " + username);
            }

            DataSource dataSource = DatabaseUtils.createDataSource(url, username, password);
            boolean success = DatabaseUtils.initDatabase(dataSource, verbose);

            if (success) {
                System.out.println("Database schema initialized successfully.");
                return 0;
            } else {
                System.err.println("Failed to initialize database schema.");
                return 1;
            }
        } catch (Exception e) {
            System.err.println("Error initializing database schema: " + e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
            return 1;
        }
    }
}