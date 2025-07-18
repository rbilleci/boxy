package boxy.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.time.Duration;

public class DataSourceProvider {

    private static HikariDataSource ds = createDataSource();

    public static synchronized DataSource dataSource() {
        if (ds == null) {
            ds = createDataSource();
        }
        return ds;
    }

    public static synchronized void close() {
        if (ds != null) {
            ds.close();
            ds = null;
        }
    }

    private static HikariDataSource createDataSource() {
        final var dbType = resolveConfigValue("DB_TYPE", "mysql");
        final var host = resolveConfigValue("DB_HOST", "localhost");
        final var port = resolveConfigValue("DB_PORT", dbType.equals("mysql") ? "3306" : "5432");
        final var name = resolveConfigValue("DB_NAME", "events_db");
        final var user = resolveConfigValue("DB_USER", "user");
        final var password = resolveConfigValue("DB_PASSWORD", "password");
        final var config = new HikariConfig();
        config.setPoolName("boxy-cp");
        config.setUsername(user);
        config.setPassword(password);
        if ("postgres".equalsIgnoreCase(dbType)) {
            config.setJdbcUrl(String.format("jdbc:postgresql://%s:%s/%s", host, port, name));
            config.addDataSourceProperty("tcpKeepAlive", "true");
            config.addDataSourceProperty("prepareThreshold", "3");
            config.addDataSourceProperty("preparedStatementCacheQueries", "256");
            config.addDataSourceProperty("preparedStatementCacheSizeMiB", "16");
            config.setConnectionTestQuery("SELECT 1");
        } else if ("mysql".equalsIgnoreCase(dbType)) {
            config.setJdbcUrl(String.format("jdbc:mysql://%s:%s/%s", host, port, name));
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
        } else {
            throw new UnsupportedOperationException();
        }
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(Duration.ofSeconds(10).toMillis());
        config.setIdleTimeout(Duration.ofMinutes(5).toMillis());
        config.setMaxLifetime(Duration.ofMinutes(30).toMillis());
        config.setLeakDetectionThreshold(Duration.ofSeconds(2).toMillis());
        config.setRegisterMbeans(true);
        return new HikariDataSource(config);
    }

    static String resolveConfigValue(final String key, final String defaultValue) {
        return System.getenv().getOrDefault(key, System.getProperty(key, defaultValue));
    }

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(ds::close, "hikari-cp-shutdown"));
    }


}
