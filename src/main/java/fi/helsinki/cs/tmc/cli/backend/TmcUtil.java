package fi.helsinki.cs.tmc.cli.backend;

import fi.helsinki.cs.tmc.cli.core.CliContext;
import fi.helsinki.cs.tmc.cli.io.Io;

import fi.helsinki.cs.tmc.core.TmcCore;
import fi.helsinki.cs.tmc.core.commands.GetUpdatableExercises.UpdateResult;
import fi.helsinki.cs.tmc.core.domain.Course;
import fi.helsinki.cs.tmc.core.domain.Exercise;
import fi.helsinki.cs.tmc.core.domain.Organization;
import fi.helsinki.cs.tmc.core.domain.ProgressObserver;
import fi.helsinki.cs.tmc.core.domain.submission.FeedbackAnswer;
import fi.helsinki.cs.tmc.core.domain.submission.SubmissionResult;
import fi.helsinki.cs.tmc.core.exceptions.FailedHttpResponseException;
import fi.helsinki.cs.tmc.core.exceptions.ObsoleteClientException;
import fi.helsinki.cs.tmc.langs.abstraction.ValidationResult;
import fi.helsinki.cs.tmc.langs.domain.RunResult;

import org.apache.commons.compress.archivers.sevenz.CLI;
import org.apache.oltu.oauth2.common.exception.OAuthProblemException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

public class TmcUtil {

    private static final Logger logger = LoggerFactory.getLogger(TmcUtil.class);

    /**
     * Check if we have internet connection.
     * This is done with making dns lookup
     * for the www.mooc.fi domain. This isn't
     * 100% exact way to check internet access,
     * but it's good enough.
     * TODO the method could be changed into requireConnection(),
     *      which also would print error message.
     *
     * @param ctx context object
     * @return true if we have internet access.
     */
    public static boolean hasConnection(CliContext ctx) {
        try {
            InetAddress.getByName("www.mooc.fi");
        } catch (Exception e) {
            TmcUtil.logger.warn("No internet", e.getCause());
            return false;
        }
        return true;
    }

    public static boolean tryToLogin(CliContext ctx, Account account, String password) {
        TmcCore core = ctx.getTmcCore();
        ctx.useAccount(account);
        Callable<Void> callable = core.authenticate(ProgressObserver.NULL_OBSERVER, password);
        //TODO restore the settings object

        try {
            callable.call();
            return true;
        } catch (Exception e) {
            if (isAuthenticationError(e)) {
                ctx.getIo().println("Incorrect username or password.");
                return false;
            }
            handleTmcExceptions(ctx, e);
            return false;
        }
    }

    public static List<Course> listCourses(CliContext ctx) {
        Callable<List<Course>> callable;
        callable = ctx.getTmcCore().listCourses(ProgressObserver.NULL_OBSERVER);

        try {
            return callable.call();
        } catch (Exception e) {
            TmcUtil.handleTmcExceptions(ctx, e);
            TmcUtil.logger.warn("Failed to get courses to list the exercises", e);
        }
        return new ArrayList<>();
    }

    public static List<Organization> getOrganizationsFromServer(CliContext ctx) {
        Callable<List<Organization>> callable;
        callable = ctx.getTmcCore().getOrganizations(ProgressObserver.NULL_OBSERVER);

        try {
            return callable.call();
        } catch (Exception e) {
            TmcUtil.handleTmcExceptions(ctx, e);
            TmcUtil.logger.error("Failed to get organizations from server", e);
        }
        return new ArrayList();
    }

    private static Course getDetails(CliContext ctx, Course course) {
        try {
            TmcCore core = ctx.getTmcCore();
            return core.getCourseDetails(ProgressObserver.NULL_OBSERVER, course).call();
        } catch (Exception e) {
            TmcUtil.handleTmcExceptions(ctx, e);
            logger.warn("Failed to get course details to list the exercises", e);
            return null;
        }
    }

    public static Course findCourse(CliContext ctx, String name) {
        List<Course> courses;
        courses = TmcUtil.listCourses(ctx);

        for (Course item : courses) {
            if (item.getName().equals(name)) {
                return TmcUtil.getDetails(ctx, item);
            }
        }
        return null;
    }

    //TODO This is exactly same method as CourseInfo.getExercise(course, name)
    public static Exercise findExercise(Course course, String name) {
        List<Exercise> exercises;
        exercises = course.getExercises();

        for (Exercise item : exercises) {
            if (item.getName().equals(name)) {
                return item;
            }
        }
        return null;
    }

    public static List<Exercise> downloadExercises(
            CliContext ctx, List<Exercise> exercises, ProgressObserver progobs) {
        try {
            TmcCore core = ctx.getTmcCore();
            return core.downloadOrUpdateExercises(progobs, exercises).call();
        } catch (Exception e) {
            TmcUtil.handleTmcExceptions(ctx, e);
            logger.warn("Failed to download exercises", e);
            return null;
        }
    }

    public static List<Exercise> downloadAllExercises(
            CliContext ctx, Course course, ProgressObserver progobs) {
        if (!course.isExercisesLoaded()) {
            course = getDetails(ctx, course);
        }
        List<Exercise> exercises = course.getExercises();
        return downloadExercises(ctx, exercises, progobs);
    }

    public static SubmissionResult submitExercise(CliContext ctx, Exercise exercise) {
        try {
            TmcCore core = ctx.getTmcCore();
            return core.submit(ProgressObserver.NULL_OBSERVER, exercise).call();
        } catch (Exception e) {
            TmcUtil.handleTmcExceptions(ctx, e);
            logger.warn("Failed to submit the exercise", e);
            return null;
        }
    }

    public static UpdateResult getUpdatableExercises(CliContext ctx, Course course) {
        try {
            TmcCore core = ctx.getTmcCore();
            return core.getExerciseUpdates(ProgressObserver.NULL_OBSERVER, course).call();
        } catch (Exception e) {
            TmcUtil.handleTmcExceptions(ctx, e);
            logger.warn("Failed to get exercise updates.", e);
            return null;
        }
    }

    public static URI sendPaste(CliContext ctx, Exercise exercise, String message) {
        try {
            TmcCore core = ctx.getTmcCore();
            return core.pasteWithComment(ProgressObserver.NULL_OBSERVER, exercise, message).call();

        } catch (Exception e) {
            TmcUtil.handleTmcExceptions(ctx, e);
            logger.error("Failed to send paste", e);
            return null;
        }
    }

    public static RunResult runLocalTests(CliContext ctx, Exercise exercise) {
        try {
            TmcCore core = ctx.getTmcCore();
            return core.runTests(ProgressObserver.NULL_OBSERVER, exercise).call();

        } catch (Exception e) {
            TmcUtil.handleTmcExceptions(ctx, e);
            logger.error("Failed to run local tests", e);
            return null;
        }
    }

    public static ValidationResult runCheckStyle(CliContext ctx, Exercise exercise) {
        try {
            TmcCore core = ctx.getTmcCore();
            return core.runCheckStyle(ProgressObserver.NULL_OBSERVER, exercise).call();
        } catch (Exception e) {
            logger.error("Failed to run checkstyle", e);
            return null;
        }
    }

    public static boolean sendFeedback(
            CliContext ctx, List<FeedbackAnswer> answers, URI feedbackUri) {
        try {
            TmcCore core = ctx.getTmcCore();
            return core.sendFeedback(ProgressObserver.NULL_OBSERVER, answers, feedbackUri).call();

        } catch (Exception e) {
            TmcUtil.handleTmcExceptions(ctx, e);
            logger.error("Couldn't send feedback", e);
            return false;
        }
    }

    private static void handleTmcExceptions(CliContext ctx, Exception exception) {
        Io io = ctx.getIo();
        Throwable cause = exception.getCause();

        if (isAuthenticationError(exception)) {
            io.errorln("Your username or password is not valid anymore.");
            return;
        }

        if (exception instanceof IllegalArgumentException) {
            logger.error("Invalid arguments", exception);
            io.errorln("Please give server, username and password in valid form.");
            return;
        }

        if (cause instanceof FailedHttpResponseException) {
            logger.error("Unable to connect to server", exception);
            io.errorln("Unable to connect to server.");
            return;
        }

        if (exception instanceof UnknownHostException) {
            logger.error("Unknown host", exception);
            io.errorln("Unknwon host, check the server address.");
            return;
        }

        if (cause instanceof ObsoleteClientException) {
            logger.error("Outdated tmc client");
            io.errorln("Your tmc-cli is outdated. Please update it.");
            ctx.getApp().runAutoUpdate();
            return;
        }

        if (cause != null && cause.getCause() instanceof UnknownHostException) {
            logger.error("No internet connection");
            io.errorln("You have no internet connection.");
            return;
        }
        logger.error("Command failed in tmc-core", exception);
        //TODO we seem to write twice error message; here and in the commands.
        io.errorln("Command failed, check tmc-cli.log file for more info");
    }

    private static boolean isAuthenticationError(Exception exception) {
        return exception.getCause() instanceof OAuthProblemException;
    }
}
