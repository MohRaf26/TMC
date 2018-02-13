package fi.helsinki.cs.tmc.cli.command;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.verifyStatic;

import fi.helsinki.cs.tmc.cli.Application;
import fi.helsinki.cs.tmc.cli.backend.Account;
import fi.helsinki.cs.tmc.cli.backend.AccountList;
import fi.helsinki.cs.tmc.cli.backend.Settings;
import fi.helsinki.cs.tmc.cli.backend.SettingsIo;
import fi.helsinki.cs.tmc.cli.backend.TmcUtil;
import fi.helsinki.cs.tmc.cli.core.CliContext;
import fi.helsinki.cs.tmc.cli.io.TestIo;
import fi.helsinki.cs.tmc.cli.io.WorkDir;
import fi.helsinki.cs.tmc.core.TmcCore;
import fi.helsinki.cs.tmc.core.domain.Course;
import fi.helsinki.cs.tmc.core.domain.Exercise;
import fi.helsinki.cs.tmc.core.domain.Organization;
import fi.helsinki.cs.tmc.core.domain.ProgressObserver;

import org.apache.commons.cli.ParseException;
import org.apache.commons.io.FileUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(PowerMockRunner.class)
@PrepareForTest({TmcUtil.class, SettingsIo.class})
public class DownloadExercisesCommandTest {

    private Application app;
    private CliContext ctx;
    private TestIo io;
    private TmcCore mockCore;
    private WorkDir workDir;
    private Path tempDir;
    private Organization testOrganization;

    @Before
    public void setUp() {
        tempDir = Paths.get(System.getProperty("java.io.tmpdir")).resolve("downloadTest");
        workDir = new WorkDir(tempDir);
        testOrganization = new Organization("test", "test", "hy", "test", false);

        io = new TestIo();
        mockCore = mock(TmcCore.class);
        ctx = new CliContext(io, mockCore, workDir);
        app = new Application(ctx);
        Account account = new Account("user", "password", testOrganization);
        ctx.useAccount(account);
        AccountList accountList = new AccountList();
        accountList.addAccount(account);

        mockStatic(TmcUtil.class);
        mockStatic(SettingsIo.class);
        when(SettingsIo.loadAccountList()).thenReturn(accountList);
    }

    @After
    public void tearDown() throws IOException {
        FileUtils.deleteDirectory(tempDir.toFile());
    }

    @Test
    public void failIfBackendFails() {
        ctx = spy(new CliContext(io, mockCore, workDir));
        app = new Application(ctx);
        doReturn(false).when(ctx).loadBackend();

        String[] args = {"download", "foo"};
        app.run(args);
        io.assertNotContains("Course doesn't exist");
    }

    @Test
    public void failIfCourseArgumentNotGiven() {
        String[] args = {"download"};
        app.run(args);
        io.assertContains("You must give");
    }

    @Test
    public void worksRightIfCourseIsNotFound() throws IOException {
        when(TmcUtil.findCourse(eq(ctx), eq("foo"))).thenReturn(null);
        String[] args = {"download", "foo"};
        app.run(args);
        io.assertContains("Course doesn't exist");
    }

    @Test
    public void worksRightIfCourseIsFound() throws IOException {
        Course course = new Course("course1");
        course.setExercises(Collections.singletonList(new Exercise("exercise")));
        List<Exercise> exercises = Collections.singletonList(new Exercise("exerciseName"));

        when(TmcUtil.findCourse(eq(ctx), eq("course1"))).thenReturn(course);
        when(
                        TmcUtil.downloadExercises(
                                eq(ctx), anyListOf(Exercise.class), any(ProgressObserver.class)))
                .thenReturn(exercises);

        String[] args = {"download", "course1"};
        app.run(args);

        File courseJson = tempDir.resolve("course1").resolve(".tmc.json").toFile();
        assertTrue(courseJson.exists());
    }

    @Test
    public void filtersCompletedExercisesByDefault() throws ParseException {
        Exercise notCompleted = new Exercise("not-completed");
        Exercise completed1 = new Exercise("completed1");
        Exercise completed2 = new Exercise("completed2");

        notCompleted.setCompleted(false);
        completed1.setCompleted(true);
        completed2.setCompleted(true);
        workDir.setWorkdir(tempDir);

        List<Exercise> filteredExercises = Collections.singletonList(notCompleted);

        Course course = new Course("course1");
        course.setExercises(Arrays.asList(completed1, notCompleted, completed2));

        when(TmcUtil.findCourse(eq(ctx), eq("course1"))).thenReturn(course);
        when(
                        TmcUtil.downloadExercises(
                                eq(ctx), anyListOf(Exercise.class), any(ProgressObserver.class)))
                .thenReturn(filteredExercises);

        String[] args = {"download", "course1"};
        app.run(args);

        io.assertContains("which 1 exercises were downloaded");
    }

    @Test
    public void getsAllExercisesWithAllSwitch() throws ParseException {
        Exercise notCompleted = new Exercise("not-completed");
        Exercise completed1 = new Exercise("completed1");
        Exercise completed2 = new Exercise("completed2");

        notCompleted.setCompleted(false);
        completed1.setCompleted(true);
        completed2.setCompleted(true);
        workDir.setWorkdir(tempDir);

        List<Exercise> exercises = Arrays.asList(completed1, notCompleted, completed2);
        Course course = new Course("course1");
        course.setExercises(exercises);

        when(TmcUtil.findCourse(eq(ctx), eq("course1"))).thenReturn(course);
        when(
                        TmcUtil.downloadExercises(
                                eq(ctx), anyListOf(Exercise.class), any(ProgressObserver.class)))
                .thenReturn(exercises);

        String[] args = {"download", "-a", "course1"};
        app.run(args);

        io.assertContains("which 3 exercises were downloaded");
    }

    @Test
    public void failsToLoadExercises() throws ParseException {
        Exercise exercise1 = new Exercise("exercise1");
        Exercise exercise2 = new Exercise("exercise2");
        Exercise exercise3 = new Exercise("exercise3");

        List<Exercise> exercises = Arrays.asList(exercise1, exercise2, exercise3);
        List<Exercise> downloaded = Arrays.asList(exercise1, exercise3);
        Course course = new Course("course1");
        course.setExercises(exercises);
        workDir.setWorkdir(tempDir);

        when(TmcUtil.findCourse(eq(ctx), eq("course1"))).thenReturn(course);
        when(
                        TmcUtil.downloadExercises(
                                eq(ctx), anyListOf(Exercise.class), any(ProgressObserver.class)))
                .thenReturn(downloaded);

        String[] args = {"download", "course1"};
        app.run(args);

        io.assertContains("The 'course1' course has 3 exercises");
        io.assertContains("of which 2 exercises were succesfully downloaded");
        io.assertContains("and of which 1 failed.");
    }

    @Test
    public void findFromMultipleServer() {
        Account account1 = new Account("", "", testOrganization);
        account1.setServerAddress("http://test.test");
        Account account2 = new Account("", "", testOrganization);
        account2.setServerAddress("http://hello.test");
        AccountList accountList = new AccountList();
        accountList.addAccount(account1);
        accountList.addAccount(account2);

        when(TmcUtil.findCourse(eq(ctx), eq("course1")))
                .thenReturn(new Course("course1"))
                .thenReturn(new Course("course2"));
        when(SettingsIo.loadAccountList()).thenReturn(accountList);

        String[] args = {"download", "course2"};
        app.run(args);
    }

    @Test
    public void findFromMultipleServerWithSameNameWithoutTakingAny() {
        Account account1 = new Account("abc", "", testOrganization);
        account1.setServerAddress("http://test.test");
        Account account2 = new Account("def", "", testOrganization);
        account2.setServerAddress("http://hello.test");
        AccountList accountList = new AccountList();
        accountList.addAccount(account1);
        accountList.addAccount(account2);

        when(TmcUtil.findCourse(eq(ctx), eq("course1")))
                .thenReturn(new Course("course1"))
                .thenReturn(new Course("course1"));
        when(SettingsIo.loadAccountList()).thenReturn(accountList);

        List<Exercise> exercises = Collections.emptyList();
        when(
                        TmcUtil.downloadExercises(
                                eq(ctx), anyListOf(Exercise.class), any(ProgressObserver.class)))
                .thenReturn(exercises);

        String[] args = {"download", "course1"};
        io.addConfirmationPrompt(false);
        io.addConfirmationPrompt(false);
        app.run(args);
        io.assertContains("There is 2 courses with same name at different servers");
        io.assertContains("Download course from http://test.test with 'abc' account");
        io.assertContains("Download course from http://hello.test with 'def' account");
        io.assertContains("The previous course was last that matched");
        io.assertAllPromptsUsed();
    }

    @Test
    public void findFromMultipleServerWithSameNameWithTakingFirst() {
        Account account1 = new Account("abc", "", testOrganization);
        account1.setServerAddress("http://test.test");
        Account account2 = new Account("def", "", testOrganization);
        account2.setServerAddress("http://hello.test");
        AccountList accountList = new AccountList();
        accountList.addAccount(account2);
        accountList.addAccount(account1);

        when(TmcUtil.findCourse(eq(ctx), eq("course1")))
                .thenReturn(new Course("course1"))
                .thenReturn(new Course("course1"));
        when(SettingsIo.loadAccountList()).thenReturn(accountList);

        String[] args = {"download", "course1"};
        io.addConfirmationPrompt(true);
        app.run(args);
        io.assertContains("There is 2 courses with same name at different servers");
        io.assertContains("Download course from http://test.test with 'abc' account");
        io.assertAllPromptsUsed();

        ArgumentCaptor<CliContext> ctxCaptor = ArgumentCaptor.forClass(CliContext.class);
        verifyStatic();
        TmcUtil.downloadExercises(
                ctxCaptor.capture(), anyListOf(Exercise.class), any(ProgressObserver.class));

        Settings usedSettings = Whitebox.getInternalState(ctxCaptor.getValue(), "settings");
        assertEquals(account1, usedSettings.getAccount());
    }

    @Test
    public void courseConfigFileDeletedIfDownloadingExercisesFails() {
        ArgumentCaptor<CliContext> ctxCaptor = ArgumentCaptor.forClass(CliContext.class);
        when(TmcUtil.downloadExercises(ctxCaptor.capture(),  anyListOf(Exercise.class), any(ProgressObserver.class))).thenReturn(null);
        String[] args = {"download", "course1"};
        app.run(args);
        File courseJson = tempDir.resolve("course1").resolve(".tmc.json").toFile();
        assertTrue(!courseJson.exists());
    }
}
