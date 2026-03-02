package boxy.cli.cmd;

import boxy.cli.ConnectionOptions;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

import java.sql.Connection;
import java.util.concurrent.Callable;

/**
 * {@code boxy db migrate} — apply pending Liquibase changesets.
 *
 * <p>Runs any changesets that have not yet been applied to the target database.
 * Equivalent to {@code boxy db init} for first-time setup; useful for incremental
 * upgrades in existing installations.
 *
 * <p>Example:
 * <pre>{@code boxy db migrate --host prod-db --db events_db --user boxy --password secret}</pre>
 */
@Command(
        name = "migrate",
        description = "Apply pending Liquibase migrations to the database.",
        mixinStandardHelpOptions = true
)
public class DbMigrateCommand implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(DbMigrateCommand.class);

    @Mixin
    private ConnectionOptions conn;

    @Option(names = {"--contexts"}, description = "Comma-separated Liquibase contexts to activate.", defaultValue = "")
    private String contexts;

    @Override
    public Integer call() {
        log.info("Running Boxy schema migrations...");
        try (final var ds = conn.dataSource();
             final Connection c = ds.getConnection()) {
            final var db = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(c));
            try (final var liquibase = new Liquibase(
                    "db/changelog/db.changelog-master.xml",
                    new ClassLoaderResourceAccessor(),
                    db)) {
                liquibase.update(contexts);
            }
            System.out.println("Migrations applied successfully.");
            return 0;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            log.error("Migration failed", e);
            return 1;
        }
    }
}
