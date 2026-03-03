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
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Map;
import javax.sql.DataSource;

/**
 * Base class for all Boxy integration tests.
 *
 * <p>Supports both MySQL and PostgreSQL via the {@code DB_TYPE} system property:
 * <ul>
 *   <li>{@code -DDB_TYPE=mysql} (default) — uses MySQL 8.0.43 Testcontainer</li>
 *   <li>{@code -DDB_TYPE=postgres} — uses PostgreSQL 16 Testcontainer</li>
 * </ul>
 *
 * <p>The database container is started once per test class. Liquibase migrations are applied
 * in {@link #setupDB()}, and all tables are truncated between tests in {@link #teardownDB()}.
 */
public abstract class BaseIT {

    /** Determines which database to use. Set via {@code -DDB_TYPE=postgres}. */
    private static final String DB_TYPE = System.getProperty("DB_TYPE", "mysql");

    @Container
    private static final JdbcDatabaseContainer<?> DATABASE = createContainer();

    protected DataSource dataSource;

    @SuppressWarnings("resource")
    private static JdbcDatabaseContainer<?> createContainer() {
        if ("postgres".equalsIgnoreCase(DB_TYPE) || "postgresql".equalsIgnoreCase(DB_TYPE)) {
            return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"))
                    .withDatabaseName("events_db")
                    .withTmpFs(Map.of("/var/lib/postgresql/data", "rw"));
        }

        // Default: MySQL
        return new MySQLContainer<>(DockerImageName.parse("mysql:8.0.43"))
                .withDatabaseName("events_db")
                .withTmpFs(Map.of("/var/lib/mysql", "rw"))
                .withCommand(
                        "mysqld", "--log-bin-trust-function-creators=1",
                        "--event_scheduler=ON",
                        "--innodb_file_per_table=ON",
                        "--innodb_flush_log_at_trx_commit=0",
                        "--sync_binlog=0",
                        "--innodb_doublewrite=0",
                        "--innodb_flush_method=nosync",
                        "--performance_schema=OFF",
                        "--innodb_autoinc_lock_mode=2"
                );
    }

    @BeforeAll
    static void setupDB() throws SQLException, LiquibaseException {
        // Set environment for DataSourceProvider
        if (DATABASE instanceof PostgreSQLContainer<?> pg) {
            System.setProperty("DB_TYPE", "postgres");
            System.setProperty("DB_HOST", pg.getHost());
            System.setProperty("DB_PORT", pg.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT).toString());
            System.setProperty("DB_NAME", pg.getDatabaseName());
            System.setProperty("DB_USER", pg.getUsername());
            System.setProperty("DB_PASSWORD", pg.getPassword());
        } else if (DATABASE instanceof MySQLContainer<?> mysql) {
            System.setProperty("DB_TYPE", "mysql");
            System.setProperty("DB_HOST", mysql.getHost());
            System.setProperty("DB_PORT", mysql.getMappedPort(MySQLContainer.MYSQL_PORT).toString());
            System.setProperty("DB_NAME", mysql.getDatabaseName());
            System.setProperty("DB_USER", mysql.getUsername());
            System.setProperty("DB_PASSWORD", mysql.getPassword());
        }

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
                tables.add(rs.getString(1));
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
                    stmt.addBatch("TRUNCATE TABLE `%s`".formatted(table));
                }
                stmt.executeBatch();
            }
            stmt.execute("SET FOREIGN_KEY_CHECKS = 1;");
        }
    }
}
