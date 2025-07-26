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
import java.io.StringWriter;
import java.io.PrintWriter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.Callable;

/**
 * Command for migrating the database schema.
 * <p>
 * This command uses Liquibase to migrate the database schema to the latest version.
 */
@Command(
        name = "migrate",
        description = "Migrate the database schema to the latest version",
        mixinStandardHelpOptions = true
)
public class MigrateCommand implements Callable<Integer> {

    @ParentCommand
    private DbCommand parent;

    @Option(names = {"--changelog"}, description = "Liquibase changelog file (default: db/changelog/db.changelog-master.xml)")
    private String changelogFile = "db/changelog/db.changelog-master.xml";

    @Option(names = {"--contexts"}, description = "Liquibase contexts to use")
    private String contexts;

    @Option(names = {"--labels"}, description = "Liquibase labels to use")
    private String labels;

    @Option(names = {"--count"}, description = "Number of changes to apply (default: all)")
    private Integer count;

    @Option(names = {"--dry-run"}, description = "Show what changes would be applied without actually applying them")
    private boolean dryRun = false;

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

                // Execute the migration
                if (dryRun) {
                    System.out.println("Dry run mode - showing pending changes:");
                    StringWriter writer = new StringWriter();
                    PrintWriter printWriter = new PrintWriter(writer);
                    
                    if (count != null) {
                        liquibase.update(count, new Contexts(contexts), new LabelExpression(labels), printWriter);
                    } else {
                        liquibase.update(new Contexts(contexts), new LabelExpression(labels), printWriter);
                    }
                    
                    System.out.println(writer.toString());
                } else {
                    System.out.println("Migrating database schema...");
                    if (count != null) {
                        System.out.println("Applying " + count + " change(s)...");
                        liquibase.update(count, new Contexts(contexts), new LabelExpression(labels));
                    } else {
                        liquibase.update(new Contexts(contexts), new LabelExpression(labels));
                    }
                    System.out.println("Database schema migrated successfully.");
                }
                
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