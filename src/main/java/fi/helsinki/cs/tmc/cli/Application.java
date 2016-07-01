package fi.helsinki.cs.tmc.cli;

import fi.helsinki.cs.tmc.cli.command.core.AbstractCommand;
import fi.helsinki.cs.tmc.cli.command.core.CommandFactory;
import fi.helsinki.cs.tmc.cli.io.Color;
import fi.helsinki.cs.tmc.cli.io.EnvironmentUtil;
import fi.helsinki.cs.tmc.cli.io.HelpGenerator;
import fi.helsinki.cs.tmc.cli.io.Io;
import fi.helsinki.cs.tmc.cli.io.ShutdownHandler;
import fi.helsinki.cs.tmc.cli.tmcstuff.WorkDir;
import fi.helsinki.cs.tmc.cli.updater.TmcCliUpdater;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * The application class for the program.
 */
public class Application {
    private static final Logger logger = LoggerFactory.getLogger(Application.class);
    private static final String previousUpdateDateKey = "update-date";
    private static final long defaultUpdateInterval = 60 * 60 * 1000;
    private static final String usage = "tmc [args] COMMAND [command-args]";

    private ShutdownHandler shutdownHandler;
    private final CliContext context;
    private final Io io;

    private final Options options;
    private final GnuParser parser;
    private String commandName;

    public Application(CliContext context) {
        this.parser = new GnuParser();
        this.options = new Options();

        this.context = context;
        this.io = context.getIo();

        options.addOption("h", "help", false, "Display help information about tmc-cli");
        options.addOption("v", "version", false, "Give the version of the tmc-cli");

        //TODO implement the inTests as context.property
        if (!context.inTests()) {
            shutdownHandler = new ShutdownHandler(context.getIo());
            shutdownHandler.enable();
        }
    }

    private boolean runCommand(String name, String[] args) {
        AbstractCommand command = CommandFactory.createCommand(this.context, name);
        if (command == null) {
            io.println("Command " + name + " doesn't exist.");
            return false;
        }

        command.execute(args, io);
        return true;
    }

    private String[] parseArgs(String[] args) {
        CommandLine line;
        try {
            line = this.parser.parse(this.options, args, true);
        } catch (ParseException e) {
            io.println("Invalid command line arguments.");
            io.println(e.getMessage());
            return null;
        }

        List<String> subArgs = new ArrayList<>(Arrays.asList(line.getArgs()));
        if (subArgs.size() > 0) {
            commandName = subArgs.remove(0);
        } else {
            commandName = "help";
        }

        if (commandName.startsWith("-")) {
            io.println("Unrecognized option: " + commandName);
            return null;
        }

        if (line.hasOption("h")) {
            // don't run the help sub-command with -h switch
            if (commandName.equals("help")) {
                runCommand("help", new String[0]);
                return null;
            }
            runCommand(commandName, new String[]{"-h"});
            return null;
        }
        if (line.hasOption("v")) {
            io.println("TMC-CLI version " + EnvironmentUtil.getVersion());
            return null;
        }
        return subArgs.toArray(new String[subArgs.size()]);
    }

    public void printHelp(String description) {
        HelpGenerator.run(io, usage, description, this.options);
    }

    public void run(String[] args) {
        context.setApp(this);

        if (!context.inTests() && versionCheck()) {
            return;
        }

        String[] commandArgs = parseArgs(args);
        if (commandArgs == null) {
            return;
        }

        runCommand(commandName, commandArgs);

        if (!context.inTests()) {
            shutdownHandler.disable();
        }
    }

    public static void main(String[] args) {
        Application app = new Application(new CliContext(null));
        app.run(args);
    }

    private boolean versionCheck() {
        Map<String, String> properties = context.getProperties();
        String previousTimestamp = properties.get(previousUpdateDateKey);
        Date previous = null;

        if (previousTimestamp != null) {
            long time;
            try {
                time = Long.parseLong(previousTimestamp);
            } catch (NumberFormatException ex) {
                io.println("The previous update date isn't number.");
                logger.warn("The previous update date isn't number.", ex);
                return false;
            }
            previous = new Date(time);
        }

        Date now = new Date();
        if (previous != null && previous.getTime() + defaultUpdateInterval > now.getTime()) {
            return false;
        }

        TmcCliUpdater update = new TmcCliUpdater(io, EnvironmentUtil.getVersion(),
                EnvironmentUtil.isWindows());
        boolean updated = update.run();

        long timestamp = now.getTime();
        properties.put(previousUpdateDateKey, Long.toString(timestamp));
        context.saveProperties();

        return updated;
    }

    //TODO rename this as getColorProperty
    public Color.AnsiColor getColor(String propertyName) {
        String propertyValue = context.getProperties().get(propertyName);
        Color.AnsiColor color = Color.getColor(propertyValue);
        if (color == null) {
            switch (propertyName) {
                case "progressbar-left":    return Color.AnsiColor.ANSI_CYAN;
                case "progressbar-right":   return Color.AnsiColor.ANSI_CYAN;
                case "testresults-left":    return Color.AnsiColor.ANSI_GREEN;
                case "testresults-right":   return Color.AnsiColor.ANSI_RED;
                default:    return Color.AnsiColor.ANSI_NONE;
            }
        }
        return color;
    }
}
