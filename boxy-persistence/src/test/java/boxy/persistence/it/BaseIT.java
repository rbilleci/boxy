package boxy.persistence.it;

import boxy.persistence.DataSourceProvider;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

import java.sql.SQLException;
import java.util.Map;

public abstract class BaseIT {

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

    protected Jdbi jdbi;


    @BeforeAll
    static void setupDB() throws SQLException, LiquibaseException {
        // Configure JDBC connections settings
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
    void setupJDBI() {
        jdbi = Jdbi.create(DataSourceProvider.dataSource());
        jdbi.installPlugin(new SqlObjectPlugin());
    }

    @AfterEach
    void teardownDB() throws SQLException {
        jdbi.useHandle(handle -> {
            final var productName = handle.getConnection()
                    .getMetaData()
                    .getDatabaseProductName();
            if ("PostgreSQL".equalsIgnoreCase(productName)) {
                teardownPGSQL(handle);
            } else if ("MySQL".equalsIgnoreCase(productName)) {
                teardownMYSQL(handle);
            } else {
                throw new UnsupportedOperationException(String.format("Database %s is not supported", productName));
            }
        });
        DataSourceProvider.close();
    }

    void teardownPGSQL(final Handle handle) {
        final var query = "SELECT string_agg(quote_ident(tableName), ', ') FROM pg_tables WHERE schemaname = 'public'";
        handle.execute(String.format(
                "TRUNCATE TABLE %s RESTART IDENTITY CASCADE;",
                handle.createQuery(query).mapTo(String.class).one()));
    }

    void teardownMYSQL(final Handle handle) {
        handle.execute("SET FOREIGN_KEY_CHECKS = 0;");
        final var tables = handle.createQuery("""
                        SELECT table_name FROM information_schema.tables
                        WHERE table_type = 'BASE TABLE' AND table_schema = DATABASE()
                        """)
                .mapTo(String.class)
                .list();
        final var batch = handle.createBatch();
        tables.forEach(table -> batch.add("TRUNCATE TABLE `" + table + "`"));
        batch.execute();
        handle.execute("SET FOREIGN_KEY_CHECKS = 1;");
    }

}
