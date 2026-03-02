package boxy.cli.cmd;

import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;

/**
 * Command group: {@code boxy event}.
 *
 * <p>Provides subcommands for publishing and listening to events.
 */
@Command(
        name = "event",
        description = "Event publishing and listening commands.",
        subcommands = {
                EventPublishCommand.class,
                EventListenCommand.class,
                HelpCommand.class
        },
        mixinStandardHelpOptions = true
)
public class EventCommand implements Runnable {

    @Override
    public void run() {
        System.out.println("Use 'boxy event --help' to see available subcommands.");
    }
}
