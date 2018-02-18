package fi.helsinki.cs.tmc.cli.command;

import fi.helsinki.cs.tmc.cli.backend.CourseInfo;
import fi.helsinki.cs.tmc.cli.backend.TmcUtil;
import fi.helsinki.cs.tmc.cli.core.AbstractCommand;
import fi.helsinki.cs.tmc.cli.core.CliContext;
import fi.helsinki.cs.tmc.cli.core.Command;
import fi.helsinki.cs.tmc.cli.io.Color;
import fi.helsinki.cs.tmc.cli.io.ColorUtil;
import fi.helsinki.cs.tmc.cli.io.EnvironmentUtil;
import fi.helsinki.cs.tmc.cli.io.ExternalsUtil;
import fi.helsinki.cs.tmc.cli.io.Io;

import fi.helsinki.cs.tmc.core.domain.Course;
import fi.helsinki.cs.tmc.core.domain.Exercise;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

import java.util.List;

@Command(name = "exercises", desc = "List the exercises for a specific course")
public class ListExercisesCommand extends AbstractCommand {

    private CliContext ctx;
    private Io io;

    @Override
    public String[] getUsages() {
        return new String[] {"[-n] [-i] COURSE"};
    }

    @Override
    public void getOptions(Options options) {
        options.addOption("n", "no-pager", false, "Don't use a pager to list the exercises");
        options.addOption("i", "internet", false, "Get the list of exercises from the server");
    }

    @Override
    public void run(CliContext context, CommandLine args) {
        this.ctx = context;
        this.io = ctx.getIo();

        String courseName = getCourseName(args);
        if (courseName == null) {
            return;
        }

        List<Exercise> exercises = getExercises(args, courseName);
        if (exercises == null) {
            return;
        }

        printExercises(courseName, exercises, !args.hasOption("n") && !EnvironmentUtil.isWindows());
    }

    private String getCourseName(CommandLine args) {
        String[] stringArgs = args.getArgs();
        if (stringArgs.length != 0) {
            return stringArgs[0];
        }
        return getCourseNameFromCurrentDirectory();
    }

    private List<Exercise> getExercises(CommandLine args, String courseName) {
        if (args.hasOption("i")) {
            return getExercisesFromServer(courseName);
        }
        return getLocalExercises(courseName);
    }

    private String getCourseNameFromCurrentDirectory() {
        CourseInfo info = getCourseInfoFromCurrentDirectory();
        if (info == null) {
            this.io.errorln(
                    "No course specified. Either run the command in a course"
                            + " directory or enter the course as a parameter.");
            return null;
        }
        return info.getCourseName();
    }

    private CourseInfo getCourseInfoFromCurrentDirectory() {
        return ctx.getCourseInfo();
    }

    private List<Exercise> getExercisesFromServer(String courseName) {
        if (!ctx.loadBackend()) {
            return null;
        }

        Course course = TmcUtil.findCourse(ctx, courseName);
        if (course == null) {
            this.io.errorln("Course '" + courseName + "' doesn't exist on the server.");
            return null;
        }

        List<Exercise> exercises = course.getExercises();
        if (exercises == null || exercises.isEmpty()) {
            this.io.errorln("Course '" + courseName + "' doesn't have any exercises.");
            return null;
        }
        return exercises;
    }

    private List<Exercise> getLocalExercises(String courseName) {
        CourseInfo info = getCourseInfoFromCurrentDirectory();
        if (info == null || !info.getCourseName().equals(courseName)) {
            this.io.errorln(
                    "You have to be in a course directory or use the -i option "
                            + "to get the exercises from the server.");
            return null;
        }

        List<Exercise> exercises = info.getExercises();
        if (exercises == null || exercises.isEmpty()) {
            this.io.errorln("Course '" + courseName + "' doesn't have any exercises.");
            return null;
        }
        return exercises;
    }

    private void printExercises(String courseName, List<Exercise> exercises, Boolean pager) {
        String str = getExercisesAsString(courseName, exercises);
        if (pager) {
            ExternalsUtil.showStringInPager(str, "exercise-list");
        } else {
            io.print(str);
        }
    }

    private String getExercisesAsString(String courseName, List<Exercise> exercises) {
        StringBuilder sb = new StringBuilder("Course name: " + courseName);
        String prevDeadline = "";

        for (Exercise exercise : exercises) {
            String deadline = CourseInfo.getExerciseDeadline(exercise);
            if (!deadline.equals(prevDeadline)) {
                sb.append("\nDeadline: ").append(deadline).append("\n");
                prevDeadline = deadline;
            }
            sb.append(getExerciseStatus(exercise));
        }
        return sb.toString();
    }

    private String getExerciseStatus(Exercise exercise) {
        // Check the exercise status in order of flag importance, for example there's
        // no need to check if deadline has passed if the exercise has been submitted
        String status;
        if (exercise.isCompleted()) {
            if (exercise.requiresReview() && !exercise.isReviewed()) {
                status = ColorUtil.colorString("  Requires review: ", Color.YELLOW);
            } else {
                status = ColorUtil.colorString("  Completed: ", Color.GREEN);
            }
        } else if (exercise.hasDeadlinePassed()) {
            status = ColorUtil.colorString("  Deadline passed: ", Color.PURPLE);
        } else if (exercise.isAttempted()) {
            status = ColorUtil.colorString("  Attempted: ", Color.BLUE);
        } else {
            status = ColorUtil.colorString("  Not completed: ", Color.RED);
        }

        status += exercise.getName() + "\n";
        return status;
    }
}
