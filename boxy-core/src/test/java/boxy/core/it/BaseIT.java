package boxy.core.it;

import boxy.core.DataSourceProvider;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;

public abstract class BaseIT {

    private static final Set<String> PROTECTED_TABLES = Set.of("heartbeat_policies", "metrics_policies", "lease_policies");

    @Container
    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(DockerImageName.parse("mysql:8.0.33"))
                    .withDatabaseName("events_db")
                    // FOR PERFORMANCE USE IN-MEMORY TMP-FS
                    .withTmpFs(Map.of("/var/lib/mysql", "rw"))
                    .withCommand(
                            // FOR TRIGGER SUPPORT
                            "mysqld", "--log-bin-trust-function-creators=1",
                            // PERFORMANCE TWEAKS
                            "--innodb_file_per_table=ON",
                            "--innodb_flush_log_at_trx_commit=0",  // never fsync on each commit
                            "--sync_binlog=0",                     // don’t fsync the binary log
                            "--innodb_doublewrite=0",              // skip double‑write buffer
                            "--innodb_flush_method=nosync",        // avoid O_DSYNC/O_DIRECT
                            "--performance_schema=OFF"             // turn off the perf schema overhead
                    );

    protected DataSource dataSource;


    @BeforeAll
    static void setupDB() throws SQLException, LiquibaseException {
        System.setProperty("DB_HOST", MYSQL.getHost());
        System.setProperty("DB_PORT", MYSQL.getMappedPort(MySQLContainer.MYSQL_PORT).toString());
        System.setProperty("DB_NAME", MYSQL.getDatabaseName());
        System.setProperty("DB_USER", MYSQL.getUsername());
        System.setProperty("DB_PASSWORD", MYSQL.getPassword());
        final var db = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(
                new JdbcConnection(DataSourceProvider.dataSource().getConnection()));
        final var liquibase = new Liquibase(
                "db/changelog/db.changelog-master.xml",
                new ClassLoaderResourceAccessor(),
                db);
        liquibase.dropAll();
        liquibase.update();
    }

    @BeforeEach
    void setupDataSource() {
        dataSource = DataSourceProvider.dataSource();
    }

    @AfterEach
    void teardownDB() throws SQLException {
        try (var conn = dataSource.getConnection()) {
            final var productName = conn.getMetaData().getDatabaseProductName();
            if ("PostgreSQL".equalsIgnoreCase(productName)) {
                teardownPGSQL(conn);
            } else if ("MySQL".equalsIgnoreCase(productName)) {
                teardownMYSQL(conn);
            } else {
                throw new UnsupportedOperationException("Database " + productName + " is not supported");
            }
        }
        DataSourceProvider.close();
    }

    void teardownPGSQL(final java.sql.Connection conn) throws SQLException {
        try (final var stmt = conn.createStatement();
             final var rs = stmt.executeQuery("SELECT quote_ident(tablename) FROM pg_tables WHERE schemaname = 'public'")) {

            final var tables = new ArrayList<String>();
            while (rs.next()) {
                final var table = rs.getString(1);
                if (!PROTECTED_TABLES.contains(table.toLowerCase())) {
                    tables.add(rs.getString(1));
                }
            }
            for (final var table : tables) {
                stmt.addBatch("ALTER TABLE %s DISABLE TRIGGER ALL;".formatted(table));
            }
            for (final var table : tables) {
                stmt.addBatch("TRUNCATE TABLE %s RESTART IDENTITY;".formatted(table));
            }
            for (final var table : tables) {
                stmt.addBatch("ALTER TABLE %s ENABLE TRIGGER ALL;".formatted(table));
            }
            stmt.executeBatch();
        }
    }

    void teardownMYSQL(final java.sql.Connection conn) throws SQLException {
        try (var stmt = conn.createStatement()) {
            stmt.execute("SET FOREIGN_KEY_CHECKS = 0;");
            try (var rs = stmt.executeQuery("SELECT table_name FROM information_schema.tables WHERE table_type = 'BASE TABLE' AND table_schema = DATABASE()")) {
                while (rs.next()) {
                    final var table = rs.getString(1);
                    if (!PROTECTED_TABLES.contains(table.toLowerCase())) {
                        stmt.addBatch("TRUNCATE TABLE `%s`".formatted(table));
                    }
                }
                stmt.executeBatch();
            }
            stmt.execute("SET FOREIGN_KEY_CHECKS = 1;");
        }
    }

}
