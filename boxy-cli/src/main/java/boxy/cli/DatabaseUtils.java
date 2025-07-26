package boxy.cli;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Utility class for database operations.
 * This class provides methods for initializing and migrating the database schema using Liquibase.
 */
public class DatabaseUtils {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseUtils.class);
    private static final String CHANGELOG_MASTER = "db/changelog/db.changelog-master.xml";

    /**
     * Creates a data source for the specified database connection parameters.
     *
     * @param url      Database URL
     * @param username Database username
     * @param password Database password
     * @return DataSource
     */
    public static DataSource createDataSource(String url, String username, String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setIdleTimeout(30000);
        config.setConnectionTimeout(10000);
        return new HikariDataSource(config);
    }

    /**
     * Initializes the database schema using Liquibase.
     *
     * @param dataSource DataSource
     * @param verbose    Enable verbose output
     * @return true if successful, false otherwise
     */
    public static boolean initDatabase(DataSource dataSource, boolean verbose) {
        try (Connection connection = dataSource.getConnection()) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            
            Liquibase liquibase = new Liquibase(
                    CHANGELOG_MASTER,
                    new ClassLoaderResourceAccessor(),
                    database);
            
            if (verbose) {
                logger.info("Initializing database schema...");
            }
            
            liquibase.update(new Contexts(), new LabelExpression());
            
            if (verbose) {
                logger.info("Database schema initialized successfully.");
            }
            
            return true;
        } catch (SQLException | LiquibaseException e) {
            logger.error("Failed to initialize database schema", e);
            return false;
        }
    }

    /**
     * Migrates the database schema using Liquibase.
     *
     * @param dataSource DataSource
     * @param verbose    Enable verbose output
     * @return true if successful, false otherwise
     */
    public static boolean migrateDatabase(DataSource dataSource, boolean verbose) {
        try (Connection connection = dataSource.getConnection()) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            
            Liquibase liquibase = new Liquibase(
                    CHANGELOG_MASTER,
                    new ClassLoaderResourceAccessor(),
                    database);
            
            if (verbose) {
                logger.info("Migrating database schema...");
            }
            
            liquibase.update(new Contexts(), new LabelExpression());
            
            if (verbose) {
                logger.info("Database schema migrated successfully.");
            }
            
            return true;
        } catch (SQLException | LiquibaseException e) {
            logger.error("Failed to migrate database schema", e);
            return false;
        }
    }
}