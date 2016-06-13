package fi.helsinki.cs.tmc.cli.command;

import static junit.framework.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.matchers.JUnitMatchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.verifyStatic;

import fi.helsinki.cs.tmc.cli.Application;
import fi.helsinki.cs.tmc.cli.io.TestIo;
import fi.helsinki.cs.tmc.cli.tmcstuff.TmcUtil;
import fi.helsinki.cs.tmc.cli.tmcstuff.WorkDir;
import fi.helsinki.cs.tmc.core.TmcCore;
import fi.helsinki.cs.tmc.core.domain.Course;
import fi.helsinki.cs.tmc.core.domain.submission.SubmissionResult;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.nio.file.Path;
import java.nio.file.Paths;

@RunWith(PowerMockRunner.class)
@PrepareForTest(TmcUtil.class)
public class SubmitCommandTest {

    private static final String COURSE_NAME = "2016-aalto-c";
    private static final String EXERCISE1_NAME = "Module_1-02_intro";
    private static final String EXERCISE2_NAME = "Module_1-04_func";

    static Path pathToDummyCourse;
    static Path pathToDummyExercise;
    static Path pathToDummyExerciseSrc;
    static Path pathToNonCourseDir;

    Application app;
    TestIo io;
    TmcCore mockCore;

    Course course;
    SubmissionResult result;
    SubmissionResult result2;

    @BeforeClass
    public static void setUpClass() {
        pathToDummyCourse = Paths.get(SubmitCommandTest.class.getClassLoader()
                .getResource("dummy-courses/" + COURSE_NAME).getPath());
        assertNotNull(pathToDummyCourse);

        pathToDummyExercise = pathToDummyCourse.resolve(EXERCISE1_NAME);
        assertNotNull(pathToDummyExercise);

        pathToDummyExerciseSrc = pathToDummyExercise.resolve("src");
        assertNotNull(pathToDummyExerciseSrc);

        pathToNonCourseDir = pathToDummyCourse.getParent();
        assertNotNull(pathToNonCourseDir);
    }

    @Before
    public void setUp() {
        io = new TestIo();
        app = new Application(io);
        mockCore = mock(TmcCore.class);
        app.setTmcCore(mockCore);

        course = new Course(COURSE_NAME);
        result = new SubmissionResult();
        result2 = new SubmissionResult();

        PowerMockito.mockStatic(TmcUtil.class);
        when(TmcUtil.findCourse(mockCore, COURSE_NAME)).thenReturn(course);
        when(TmcUtil.submitExercise(mockCore, course, EXERCISE1_NAME)).thenReturn(result);
        when(TmcUtil.submitExercise(mockCore, course, EXERCISE2_NAME)).thenReturn(result2);
    }

    @Test
    public void testSuccessInExerciseRoot() {
        app.setWorkdir(new WorkDir(pathToDummyExercise));
        app.run(new String[]{"submit"});
        assertThat(io.out(), containsString("Submitting: " + EXERCISE1_NAME));

        verifyStatic(times(1));
        TmcUtil.submitExercise(mockCore, course, EXERCISE1_NAME);
    }

    @Test
    public void canSubmitFromCourseDirIfExerciseNameIsGiven() {
        app.setWorkdir(new WorkDir(pathToDummyCourse));
        app.run(new String[]{"submit", EXERCISE1_NAME});
        assertThat(io.out(), containsString("Submitting: " + EXERCISE1_NAME));

        verifyStatic(times(1));
        TmcUtil.submitExercise(mockCore, course, EXERCISE1_NAME);
    }

    @Test
    public void canSubmitMultipleExercisesIfNamesAreGiven() {
        app.setWorkdir(new WorkDir(pathToDummyCourse));
        app.run(new String[]{"submit", EXERCISE1_NAME, EXERCISE2_NAME});
        assertThat(io.out(), containsString("Submitting: " + EXERCISE1_NAME));
        assertThat(io.out(), containsString("Submitting: " + EXERCISE2_NAME));

        verifyStatic(times(1));
        TmcUtil.submitExercise(mockCore, course, EXERCISE1_NAME);

        verifyStatic(times(1));
        TmcUtil.submitExercise(mockCore, course, EXERCISE2_NAME);
    }

    @Test
    public void submitsAllExercisesFromCourseDirIfNoNameIsGiven() {
        app.setWorkdir(new WorkDir(pathToDummyCourse));
        app.run(new String[]{"submit"});
        assertThat(io.out(), containsString("Submitting: " + EXERCISE1_NAME));
        assertThat(io.out(), containsString("Submitting: " + EXERCISE2_NAME));
        assertEquals(2, countSubstring("Submitting: ", io.out()));

        verifyStatic(times(1));
        TmcUtil.submitExercise(mockCore, course, EXERCISE1_NAME);

        verifyStatic(times(1));
        TmcUtil.submitExercise(mockCore, course, EXERCISE2_NAME);
    }

    @Test
    public void doesNotSubmitExtraExercisesFromExerciseRoot() {
        app.setWorkdir(new WorkDir(pathToDummyExercise));
        app.run(new String[]{"submit"});
        assertEquals(1, countSubstring("Submitting: ", io.out()));
    }

    @Test
    public void doesNotSubmitExtraExercisesFromCourseDir() {
        app.setWorkdir(new WorkDir(pathToDummyCourse));
        app.run(new String[]{"submit", EXERCISE1_NAME});
        assertEquals(1, countSubstring("Submitting: ", io.out()));
    }

    @Test
    public void abortIfInvalidCmdLineArgumentIsGiven() {
        app.setWorkdir(new WorkDir(pathToDummyCourse));
        app.run(new String[]{"submit", EXERCISE1_NAME, "-foo"});
        assertThat(io.out(), containsString("Invalid command line argument"));

        verifyStatic(times(0));
        TmcUtil.submitExercise(mockCore, course, EXERCISE1_NAME);
    }

    @Test
    public void abortIfInvalidExerciseNameIsGivenAsArgument() {
        app.setWorkdir(new WorkDir(pathToDummyCourse));
        app.run(new String[]{"submit", "foo"});
        assertThat(io.out(), containsString("Could not find exercise 'foo'"));
        assertEquals(0, countSubstring("Submitting: ", io.out()));
    }

    @Test
    public void abortGracefullyIfNotInCourseDir() {
        app.setWorkdir(new WorkDir(pathToNonCourseDir));
        app.run(new String[]{"submit"});
        assertThat(io.out(), containsString("Not a course directory"));

        verifyStatic(times(0));
        TmcUtil.submitExercise(mockCore, course, EXERCISE1_NAME);
    }

    @Test
    public void abortGracefullyIfCannotFetchCourceInfoFromServer() {
        when(TmcUtil.findCourse(mockCore, COURSE_NAME)).thenReturn(null);

        app.setWorkdir(new WorkDir(pathToDummyCourse));
        app.run(new String[]{"submit", EXERCISE1_NAME});

        assertThat(io.out(),
                containsString("Could not fetch course info from server."));
        assertEquals(0, countSubstring("Submitting: ", io.out()));

        verifyStatic(times(0));
        TmcUtil.submitExercise(mockCore, course, EXERCISE1_NAME);
    }

    @Test
    public void showFailMsgIfSubmissionFailsInCore() {
        when(TmcUtil.submitExercise(mockCore, course, EXERCISE1_NAME))
                .thenReturn(null);

        app.setWorkdir(new WorkDir(pathToDummyCourse));
        app.run(new String[]{"submit", EXERCISE1_NAME});

        assertEquals(1, countSubstring("Submitting: ", io.out()));
        assertEquals(1, countSubstring("Submission failed.", io.out()));

        verifyStatic(times(1));
        TmcUtil.submitExercise(mockCore, course, EXERCISE1_NAME);
    }

    @Test
    @Ignore("WIP: Submitting from subdirs doesn't work yet. Unignore once done")
    public void canSubmitFromExerciseSubdirs() {
        app.setWorkdir(new WorkDir(pathToDummyExerciseSrc));
        app.run(new String[]{"submit"});

        assertThat(io.out(), containsString("Submitting: " + EXERCISE1_NAME));
        verifyStatic(times(1));
        TmcUtil.submitExercise(mockCore, course, EXERCISE1_NAME);
    }

    private static int countSubstring(String subStr, String str) {
        return (str.length() - str.replace(subStr, "").length()) / subStr.length();
    }
}