package boxy.cli;

import boxy.core.DataSourceProvider;
import picocli.CommandLine.Option;

import javax.sql.DataSource;

/**
 * Reusable picocli mixin that adds database connection options to any command.
 *
 * <p>Each option falls back to the corresponding environment variable, which in turn falls back
 * to the default used by {@link DataSourceProvider}. Commands that include this mixin can pass
 * the resolved values to {@link DataSourceProvider#resolveConfigValue(String, String)} or call
 * {@link #dataSource()} directly.
 *
 * <p>Usage in a command class:
 * <pre>{@code
 * @Mixin
 * ConnectionOptions conn;
 *
 * @Override
 * public Integer call() {
 *     try (DataSource ds = conn.dataSource()) { … }
 * }
 * }</pre>
 */
public class ConnectionOptions {

    @Option(names = {"--db-type"}, description = "Database type: mysql (default) or postgres. Overrides DB_TYPE.", defaultValue = "${DB_TYPE:-mysql}")
    private String dbType;

    @Option(names = {"--host"}, description = "Database hostname. Overrides DB_HOST.", defaultValue = "${DB_HOST:-localhost}")
    private String host;

    @Option(names = {"--port"}, description = "Database port. Overrides DB_PORT.", defaultValue = "${DB_PORT:-0}")
    private int port;

    @Option(names = {"--db"}, description = "Database/schema name. Overrides DB_NAME.", defaultValue = "${DB_NAME:-events_db}")
    private String dbName;

    @Option(names = {"--user"}, description = "Database user. Overrides DB_USER.", defaultValue = "${DB_USER:-user}")
    private String user;

    @Option(names = {"--password"}, description = "Database password. Overrides DB_PASSWORD.", defaultValue = "${DB_PASSWORD:-password}", interactive = true, echo = false)
    private String password;

    /**
     * Applies the resolved connection parameters to system properties so that
     * {@link DataSourceProvider} picks them up, then returns the shared {@link DataSource}.
     *
     * @return a configured {@link DataSource}
     */
    public DataSource dataSource() {
        if (!dbType.isBlank()) System.setProperty("DB_TYPE", dbType);
        if (!host.isBlank()) System.setProperty("DB_HOST", host);
        if (port > 0) System.setProperty("DB_PORT", String.valueOf(port));
        if (!dbName.isBlank()) System.setProperty("DB_NAME", dbName);
        if (!user.isBlank()) System.setProperty("DB_USER", user);
        if (!password.isBlank()) System.setProperty("DB_PASSWORD", password);
        return DataSourceProvider.dataSource();
    }
}
