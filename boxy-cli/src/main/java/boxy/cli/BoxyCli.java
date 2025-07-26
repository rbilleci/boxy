package boxy.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

/**
 * Main entry point for the Boxy CLI application.
 * This class defines the top-level command and subcommands.
 */
@Command(
        name = "boxy",
        description = "Boxy CLI - Command line interface for Boxy",
        mixinStandardHelpOptions = true,
        version = "1.0",
        subcommands = {
                DbCommand.class
        }
)
public class BoxyCli implements Callable<Integer> {

    @Option(names = {"-v", "--verbose"}, description = "Enable verbose output")
    private boolean verbose;

    /**
     * Main entry point for the application.
     *
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        int exitCode = new CommandLine(new BoxyCli()).execute(args);
        System.exit(exitCode);
    }

    /**
     * This method is called when the command is executed without any subcommands.
     *
     * @return Exit code
     */
    @Override
    public Integer call() {
        // Show help if no subcommand is specified
        CommandLine.usage(this, System.out);
        return 0;
    }
}