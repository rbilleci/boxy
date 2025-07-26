package boxy.cli.commands.db;

import boxy.cli.commands.DbCommand;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.Callable;

/**
 * Command for initializing the database schema.
 * <p>
 * This command uses Liquibase to initialize the database schema.
 */
@Command(
        name = "init",
        description = "Initialize the database schema",
        mixinStandardHelpOptions = true
)
public class InitCommand implements Callable<Integer> {

    @ParentCommand
    private DbCommand parent;

    @Option(names = {"--changelog"}, description = "Liquibase changelog file (default: db/changelog/db.changelog-master.xml)")
    private String changelogFile = "db/changelog/db.changelog-master.xml";

    @Option(names = {"--drop-first"}, description = "Drop all database objects before initializing")
    private boolean dropFirst = false;

    @Option(names = {"--contexts"}, description = "Liquibase contexts to use")
    private String contexts;

    @Option(names = {"--labels"}, description = "Liquibase labels to use")
    private String labels;

    @Override
    public Integer call() {
        try {
            // Load the JDBC driver
            Class.forName(parent.getDriverClass());

            // Create a connection to the database
            try (Connection connection = DriverManager.getConnection(
                    parent.getJdbcUrl(),
                    parent.getUsername(),
                    parent.getPassword())) {

                // Set up Liquibase
                Database database = DatabaseFactory.getInstance()
                        .findCorrectDatabaseImplementation(new JdbcConnection(connection));
                
                Liquibase liquibase = new Liquibase(
                        changelogFile,
                        new ClassLoaderResourceAccessor(),
                        database);

                // Execute the update
                System.out.println("Initializing database schema...");
                
                if (dropFirst) {
                    System.out.println("Dropping all database objects...");
                    liquibase.dropAll();
                }
                
                liquibase.update(new Contexts(contexts), new LabelExpression(labels));
                
                System.out.println("Database schema initialized successfully.");
                return 0;
            }
        } catch (ClassNotFoundException e) {
            System.err.println("Error: JDBC driver not found: " + e.getMessage());
            return 1;
        } catch (SQLException e) {
            System.err.println("Error: Database connection failed: " + e.getMessage());
            return 1;
        } catch (LiquibaseException e) {
            System.err.println("Error: Liquibase operation failed: " + e.getMessage());
            return 1;
        }
    }
}