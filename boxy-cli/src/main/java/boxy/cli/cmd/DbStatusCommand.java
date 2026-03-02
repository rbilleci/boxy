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
 * {@code boxy db status} — display current schema version and pending changesets.
 *
 * <p>Runs {@code liquibase status} to show which changesets are pending and which have
 * already been applied.
 *
 * <p>Example:
 * <pre>{@code boxy db status --host localhost --db events_db}</pre>
 */
@Command(
        name = "status",
        description = "Show the current Liquibase schema version and any pending changesets.",
        mixinStandardHelpOptions = true
)
public class DbStatusCommand implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(DbStatusCommand.class);

    @Mixin
    private ConnectionOptions conn;

    @Override
    public Integer call() {
        try (final var ds = conn.dataSource();
             final Connection c = ds.getConnection()) {
            final var db = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(c));
            try (final var liquibase = new Liquibase(
                    "db/changelog/db.changelog-master.xml",
                    new ClassLoaderResourceAccessor(),
                    db)) {
                liquibase.reportStatus(true, "", new java.io.OutputStreamWriter(System.out));
            }
            return 0;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            log.error("Status check failed", e);
            return 1;
        }
    }
}
