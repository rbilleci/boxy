package boxy.cli.cmd;

import boxy.cli.ConnectionOptions;
import boxy.mysql.repository.NamespaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;

import java.util.concurrent.Callable;

/**
 * Command group: {@code boxy namespace}.
 *
 * <p>Provides subcommands for creating and deleting namespaces.
 */
@Command(
        name = "namespace",
        description = "Namespace management commands.",
        subcommands = {
                NamespaceCommand.CreateCommand.class,
                NamespaceCommand.DeleteCommand.class,
                HelpCommand.class
        },
        mixinStandardHelpOptions = true
)
public class NamespaceCommand implements Runnable {

    @Override
    public void run() {
        System.out.println("Use 'boxy namespace --help' to see available subcommands.");
    }

    @Command(name = "create", description = "Create a namespace path.", mixinStandardHelpOptions = true)
    static class CreateCommand implements Callable<Integer> {
        private static final Logger log = LoggerFactory.getLogger(CreateCommand.class);

        @Mixin ConnectionOptions conn;

        @Parameters(index = "0", description = "Namespace path to create (e.g. tenant-a/payments)")
        private String path;

        @Override
        public Integer call() {
            try {
                new NamespaceRepository(conn.dataSource()).create(path);
                System.out.println("Namespace created: " + path);
                return 0;
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                log.error("Namespace create failed", e);
                return 1;
            }
        }
    }

    @Command(name = "delete", description = "Delete a namespace and all its children.", mixinStandardHelpOptions = true)
    static class DeleteCommand implements Callable<Integer> {
        private static final Logger log = LoggerFactory.getLogger(DeleteCommand.class);

        @Mixin ConnectionOptions conn;

        @Parameters(index = "0", description = "Namespace path to delete")
        private String path;

        @Override
        public Integer call() {
            try {
                new NamespaceRepository(conn.dataSource()).delete(path);
                System.out.println("Namespace deleted: " + path);
                return 0;
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                log.error("Namespace delete failed", e);
                return 1;
            }
        }
    }
}
