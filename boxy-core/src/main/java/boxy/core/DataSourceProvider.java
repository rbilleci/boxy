package boxy.core;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.time.Duration;

/**
 * Factory and singleton holder for the application-scoped HikariCP connection pool.
 *
 * <p>Configuration is read from environment variables with system-property fallback:
 * <ul>
 *   <li>{@code DB_TYPE}     — {@code mysql} (default) or {@code postgres}</li>
 *   <li>{@code DB_HOST}     — database hostname (default: {@code localhost})</li>
 *   <li>{@code DB_PORT}     — database port (default: 3306 for MySQL, 5432 for PostgreSQL)</li>
 *   <li>{@code DB_NAME}     — schema/database name (default: {@code events_db})</li>
 *   <li>{@code DB_USER}     — database user (default: {@code user})</li>
 *   <li>{@code DB_PASSWORD} — database password (default: {@code password})</li>
 * </ul>
 *
 * <p>A JVM shutdown hook closes the pool automatically.
 */
public class DataSourceProvider {

    private static final Logger log = LoggerFactory.getLogger(DataSourceProvider.class);

    private static HikariDataSource ds = createDataSource();

    /**
     * Returns the shared {@link DataSource}, creating the connection pool on first call.
     *
     * @return the application-scoped {@link DataSource}
     */
    public static synchronized DataSource dataSource() {
        if (ds == null) {
            ds = createDataSource();
        }
        return ds;
    }

    /**
     * Closes the shared connection pool and sets the reference to {@code null}.
     * A subsequent call to {@link #dataSource()} will create a new pool.
     */
    public static synchronized void close() {
        if (ds != null) {
            ds.close();
            ds = null;
        }
    }

    private static HikariDataSource createDataSource() {
        final var dbType = resolveConfigValue("DB_TYPE", "mysql").toLowerCase();
        final var host   = resolveConfigValue("DB_HOST", "localhost");
        final var name   = resolveConfigValue("DB_NAME", "events_db");
        final var user   = resolveConfigValue("DB_USER", "user");
        final var pass   = resolveConfigValue("DB_PASSWORD", "password");

        final var config = new HikariConfig();
        config.setPoolName("boxy-cp");
        config.setUsername(user);
        config.setPassword(pass);

        // Switch expression (JDK 14+) replaces the previous if-else chain.
        // Each branch sets the JDBC URL and driver-specific optimisation properties.
        switch (dbType) {
            case "postgres" -> {
                final var port = resolveConfigValue("DB_PORT", "5432");
                config.setJdbcUrl("jdbc:postgresql://%s:%s/%s".formatted(host, port, name));
                config.addDataSourceProperty("tcpKeepAlive", "true");
                config.addDataSourceProperty("prepareThreshold", "3");
                config.addDataSourceProperty("preparedStatementCacheQueries", "256");
                config.addDataSourceProperty("preparedStatementCacheSizeMiB", "16");
                config.setConnectionTestQuery("SELECT 1");
            }
            case "mysql" -> {
                final var port = resolveConfigValue("DB_PORT", "3306");
                config.setJdbcUrl("jdbc:mysql://%s:%s/%s".formatted(host, port, name));
                config.addDataSourceProperty("cachePrepStmts", "true");
                config.addDataSourceProperty("prepStmtCacheSize", "256");
                config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
                config.addDataSourceProperty("useServerPrepStmts", "true");
                config.addDataSourceProperty("rewriteBatchedStatements", "true");
                config.addDataSourceProperty("cacheResultSetMetadata", "true");
                config.addDataSourceProperty("cacheServerConfiguration", "true");
                config.addDataSourceProperty("elideSetAutoCommits", "true");
                config.addDataSourceProperty("maintainTimeStats", "false");
                config.addDataSourceProperty("useSSL", "false");
                config.addDataSourceProperty("serverTimezone", "UTC");
            }
            default -> throw new UnsupportedOperationException(
                    "Unsupported DB_TYPE '%s'. Supported values: mysql, postgres".formatted(dbType));
        }

        config.setMaximumPoolSize(10);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(Duration.ofSeconds(10).toMillis());
        config.setIdleTimeout(Duration.ofMinutes(5).toMillis());
        config.setMaxLifetime(Duration.ofMinutes(30).toMillis());
        config.setLeakDetectionThreshold(Duration.ofSeconds(2).toMillis());
        config.setRegisterMbeans(true);

        log.info("Creating HikariCP pool: db_type={} host={} db={}", dbType, host, name);
        return new HikariDataSource(config);
    }

    /**
     * Resolves a configuration value from environment variables, with system-property fallback
     * and a hard-coded default.
     *
     * @param key          the environment variable / system property name
     * @param defaultValue the value to use if neither the env var nor system property is set
     * @return the resolved value; never {@code null}
     */
    static String resolveConfigValue(final String key, final String defaultValue) {
        return System.getenv().getOrDefault(key, System.getProperty(key, defaultValue));
    }

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(DataSourceProvider::close, "hikari-cp-shutdown"));
    }
}
