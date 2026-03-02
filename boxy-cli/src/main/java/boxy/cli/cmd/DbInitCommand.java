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

import java.sql.Connection;
import java.util.concurrent.Callable;

/**
 * {@code boxy db init} — initialize the Boxy schema in a new database.
 *
 * <p>Runs all Liquibase changesets from {@code db/changelog/db.changelog-master.xml}
 * (bundled in {@code boxy-db}) against the configured database. Safe to run against
 * an already-initialized database: Liquibase is idempotent.
 *
 * <p>Example:
 * <pre>{@code
 * boxy db init --host localhost --db events_db --user boxy --password secret
 * }</pre>
 */
@Command(
        name = "init",
        description = "Initialize the Boxy database schema (runs all Liquibase changesets).",
        mixinStandardHelpOptions = true
)
public class DbInitCommand implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(DbInitCommand.class);

    @Mixin
    private ConnectionOptions conn;

    @Override
    public Integer call() {
        log.info("Initializing Boxy schema...");
        try (final var ds = conn.dataSource();
             final Connection c = ds.getConnection()) {
            final var db = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(c));
            try (final var liquibase = new Liquibase(
                    "db/changelog/db.changelog-master.xml",
                    new ClassLoaderResourceAccessor(),
                    db)) {
                liquibase.update("");
            }
            System.out.println("Schema initialized successfully.");
            return 0;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            log.error("Schema initialization failed", e);
            return 1;
        }
    }
}
