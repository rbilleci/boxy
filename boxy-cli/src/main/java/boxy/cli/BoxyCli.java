package boxy.cli;

import boxy.cli.commands.DbCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

/**
 * Main entry point for the Boxy CLI application.
 * <p>
 * The CLI follows an AWS CLI-like structure with commands and subcommands.
 * Example: boxy db init --url jdbc:mysql://localhost:3306/boxy
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
     * It displays the help message.
     *
     * @return Exit code
     */
    @Override
    public Integer call() {
        // If no subcommand is specified, show help
        new CommandLine(this).usage(System.out);
        return 0;
    }
}