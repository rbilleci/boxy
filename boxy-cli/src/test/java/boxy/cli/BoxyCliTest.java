package boxy.cli;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the Boxy CLI.
 * <p>
 * This test uses TestContainers to start a MySQL container and tests the CLI commands against it.
 */
@Testcontainers
public class BoxyCliTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("boxy")
            .withUsername("boxy")
            .withPassword("boxy");

    @Test
    public void testDbInitCommand() {
        // Capture stdout
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outContent));

        try {
            // Execute the CLI command
            String[] args = {
                    "db", "init",
                    "--url", MYSQL.getJdbcUrl(),
                    "--username", MYSQL.getUsername(),
                    "--password", MYSQL.getPassword()
            };
            BoxyCli.main(args);

            // Verify the output
            String output = outContent.toString();
            assertThat(output).contains("Initializing database schema");
            assertThat(output).contains("Database schema initialized successfully");
        } finally {
            // Restore stdout
            System.setOut(originalOut);
        }
    }

    @Test
    public void testDbMigrateCommand() {
        // First initialize the database
        testDbInitCommand();

        // Capture stdout
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outContent));

        try {
            // Execute the CLI command
            String[] args = {
                    "db", "migrate",
                    "--url", MYSQL.getJdbcUrl(),
                    "--username", MYSQL.getUsername(),
                    "--password", MYSQL.getPassword()
            };
            BoxyCli.main(args);

            // Verify the output
            String output = outContent.toString();
            assertThat(output).contains("Migrating database schema");
        } finally {
            // Restore stdout
            System.setOut(originalOut);
        }
    }

    @Test
    public void testDbMigrateCommandWithDryRun() {
        // First initialize the database
        testDbInitCommand();

        // Capture stdout
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outContent));

        try {
            // Execute the CLI command
            String[] args = {
                    "db", "migrate",
                    "--url", MYSQL.getJdbcUrl(),
                    "--username", MYSQL.getUsername(),
                    "--password", MYSQL.getPassword(),
                    "--dry-run"
            };
            BoxyCli.main(args);

            // Verify the output
            String output = outContent.toString();
            assertThat(output).contains("Dry run mode - showing pending changes");
        } finally {
            // Restore stdout
            System.setOut(originalOut);
        }
    }
}