package fi.helsinki.cs.tmc.cli.command;

import static fi.helsinki.cs.tmc.langs.domain.RunResult.Status.PASSED;

import fi.helsinki.cs.tmc.cli.Application;
import fi.helsinki.cs.tmc.cli.command.core.AbstractCommand;
import fi.helsinki.cs.tmc.cli.command.core.Command;
import fi.helsinki.cs.tmc.cli.io.Color;
import fi.helsinki.cs.tmc.cli.io.Io;
import fi.helsinki.cs.tmc.cli.io.ResultPrinter;
import fi.helsinki.cs.tmc.cli.io.TmcCliProgressObserver;
import fi.helsinki.cs.tmc.cli.tmcstuff.CourseInfo;
import fi.helsinki.cs.tmc.cli.tmcstuff.CourseInfoIo;
import fi.helsinki.cs.tmc.cli.tmcstuff.Settings;
import fi.helsinki.cs.tmc.cli.tmcstuff.WorkDir;

import fi.helsinki.cs.tmc.core.TmcCore;
import fi.helsinki.cs.tmc.core.domain.Exercise;
import fi.helsinki.cs.tmc.core.domain.ProgressObserver;
import fi.helsinki.cs.tmc.langs.domain.RunResult;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;


@Command(name = "test", desc = "Run local exercise tests")
public class RunTestsCommand extends AbstractCommand {

    private static final Logger logger
            = LoggerFactory.getLogger(RunTestsCommand.class);

//    private Io io;
    private boolean showPassed;
    private boolean showDetails;

    @Override
    public void getOptions(Options options) {
        options.addOption("a", "all", false, "Show all test results");
        options.addOption("d", "details", false, "Show detailed error message");
    }

    @Override
    public void run(CommandLine args, Io io) {
//        this.io = io;

        String[] exercisesFromArgs = parseArgs(args);
        if (exercisesFromArgs == null) {
            return;
        }

        Application app = getApp();
        WorkDir workDir = app.getWorkDir();
        for (String exercise : exercisesFromArgs) {
            if (!workDir.addPath(exercise)) {
                io.println("Error: " + exercise + " is not a valid exercise.");
                return;
            }
        }
        List<String> exerciseNames = workDir.getExerciseNames();

        if (exerciseNames.isEmpty()) {
            io.println("You have to be in a course directory to run tests");
            return;
        }
        CourseInfo info = CourseInfoIo.load(workDir.getConfigFile());

        // Local tests don't require login so make sure tmcCore is never null.
        app.createTmcCore(new Settings());

        TmcCore core = app.getTmcCore();
        if (core == null) {
            return;
        }

        ResultPrinter resultPrinter
                = new ResultPrinter(io, this.showDetails, this.showPassed);
        RunResult runResult;
        Boolean isOnlyExercise = exerciseNames.size() == 1;

        Color.AnsiColor color1 = app.getColor("testresults-left");
        Color.AnsiColor color2 = app.getColor("testresults-right");

        try {
            int total = 0;
            int passed = 0;
            // TODO: use the proper progress observer once core/langs uses it correctly
            for (String name : exerciseNames) {

                io.println(Color.colorString("Testing: " + name, Color.AnsiColor.ANSI_YELLOW));
                //name = name.replace("-", File.separator);
                Exercise exercise = info.getExercise(name);
//                Exercise exercise = new Exercise(name, courseName);

                // TmcCliProgressObserver progobs = new TmcCliProgressObserver(io);
                runResult = core.runTests(ProgressObserver.NULL_OBSERVER, exercise).call();
                // progobs.end(0);

                resultPrinter.printRunResult(runResult, exercise.isCompleted(),
                        isOnlyExercise, color1, color2);
                total += runResult.testResults.size();
                passed += ResultPrinter.passedTests(runResult.testResults);
                exercise.setAttempted(true);
                if (runResult.status == PASSED && !exercise.isCompleted()) {
                    // add exercise to locally tested exercises
                    if (!info.getLocalCompletedExercises().contains(exercise.getName())) {
                        info.getLocalCompletedExercises().add(exercise.getName());
                    }
                } else {
                    if (info.getLocalCompletedExercises().contains(exercise.getName())) {
                        info.getLocalCompletedExercises().remove(exercise.getName());
                    }
                }
            }
            CourseInfoIo.save(info, workDir.getConfigFile());
            if (total > 0 && !isOnlyExercise) {
                // Print a progress bar showing how the ratio of passed exercises
                // But only if more than one exercise was tested
                io.println("");
                io.println("Total tests passed: " + passed + "/" + total);
                io.println(TmcCliProgressObserver.getPassedTestsBar(
                        passed, total, color1, color2));
            }
        } catch (Exception ex) {
            io.println("Failed to run tests.\n"
                    + ex.getMessage());
            logger.error("Failed to run tests.", ex);
        }
    }

    private String[] parseArgs(CommandLine args) {
        this.showPassed = args.hasOption("a");
        this.showDetails = args.hasOption("d");
        return args.getArgs();
    }
}
