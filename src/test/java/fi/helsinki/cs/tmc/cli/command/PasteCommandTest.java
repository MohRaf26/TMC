package fi.helsinki.cs.tmc.cli.command;

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.verifyStatic;

import fi.helsinki.cs.tmc.cli.Application;
import fi.helsinki.cs.tmc.cli.backend.CourseInfo;
import fi.helsinki.cs.tmc.cli.backend.CourseInfoIo;
import fi.helsinki.cs.tmc.cli.backend.TmcUtil;
import fi.helsinki.cs.tmc.cli.core.CliContext;
import fi.helsinki.cs.tmc.cli.io.ExternalsUtil;
import fi.helsinki.cs.tmc.cli.io.TestIo;
import fi.helsinki.cs.tmc.cli.io.WorkDir;

import fi.helsinki.cs.tmc.core.TmcCore;
import fi.helsinki.cs.tmc.core.domain.Exercise;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ExternalsUtil.class, CourseInfoIo.class, TmcUtil.class})
public class PasteCommandTest {

    private Application app;
    private CliContext ctx;
    private TestIo io;
    private TmcCore mockCore;
    private WorkDir workDir;
    private ArrayList<String> exerciseNames;
    private Exercise exercise;

    private final URI pasteUri;

    public PasteCommandTest() throws URISyntaxException {
        pasteUri = new URI("www.abc.url");
    }

    @Before
    public void setup() throws URISyntaxException {
        Path tempDir = Paths.get(System.getProperty("java.io.tmpdir")).resolve("paste-test");
        io = new TestIo();

        workDir = mock(WorkDir.class);
        when(workDir.getCourseDirectory()).thenReturn(tempDir);
        when(workDir.getConfigFile()).thenReturn((tempDir.resolve(CourseInfoIo.COURSE_CONFIG)));
        exerciseNames = new ArrayList<>();
        exerciseNames.add("paste-exercise");
        when(workDir.getExerciseNames()).thenReturn(exerciseNames);
        when(workDir.addPath()).thenReturn(true);
        when(workDir.addPath(anyString())).thenReturn(true);

        mockCore = mock(TmcCore.class);

        ctx = new CliContext(io, mockCore, workDir);
        app = new Application(ctx);

        CourseInfo mockCourseInfo = mock(CourseInfo.class);
        exercise = new Exercise("paste-exercise");
        when(mockCourseInfo.getExercise("paste-exercise")).thenReturn(exercise);

        mockStatic(TmcUtil.class);
        mockStatic(ExternalsUtil.class);
        when(ExternalsUtil
                .getUserEditedMessage(anyString(), anyString(), anyBoolean()))
                .thenReturn("This is my paste message!");

        mockStatic(CourseInfoIo.class);
        when(CourseInfoIo
                .load(any(Path.class)))
                .thenReturn(mockCourseInfo);
        when(CourseInfoIo
                .save(any(CourseInfo.class), any(Path.class)))
                .thenReturn(true);
    }

    @Test
    public void failIfCoreIsNull() {
        ctx = spy(new CliContext(io, mockCore, workDir));
        app = new Application(ctx);
        doReturn(false).when(ctx).loadBackend();

        String[] args = {"paste"};
        app.run(args);
        io.assertNotContains("No exercise specified");
    }

    @Test
    public void pasteRunsRightWithoutArguments() throws URISyntaxException {
        when(TmcUtil.sendPaste(eq(ctx), any(Exercise.class), anyString()))
                .thenReturn(pasteUri);
        io.addConfirmationPrompt(true);
        app.run(new String[] {"paste", "paste-exercise"});

        io.assertContains("Paste sent for exercise paste-exercise");
        assertTrue("Prints the paste URI",
                io.out().contains(pasteUri.toString()));
        io.assertAllPromptsUsed();

        verifyStatic(times(1));
        ExternalsUtil.getUserEditedMessage(anyString(), anyString(), anyBoolean());

        verifyStatic(times(1));
        TmcUtil.sendPaste(eq(ctx), any(Exercise.class), anyString());
    }
    
    @Test
    public void pasteRunsRightwithTooManyArguments() {
        app.run(new String[] {"paste", "paste-exercise", "secondArgument"});
        io.assertContains("Error: Too many arguments.");
    }
    
    @Test
    public void pasteRunsRightWithMessageSwitchWithMessage() {
        when(TmcUtil.sendPaste(eq(ctx), any(Exercise.class), anyString()))
                .thenReturn(pasteUri);
        app.run(new String[] {"paste", "-m", "This is a message given as an argument",
                "paste-exercise"});

        io.assertContains("Paste sent for exercise paste-exercise");
        assertTrue("Prints the paste URI",
                io.out().contains(pasteUri.toString()));

        verifyStatic(Mockito.never());
        ExternalsUtil.getUserEditedMessage(anyString(), anyString(), anyBoolean());

        verifyStatic(Mockito.times(1));
        TmcUtil.sendPaste(eq(ctx), eq(exercise),
                eq("This is a message given as an argument"));
    }

    @Test
    public void pasteFailsWithMessageSwitchWithoutMessage() {
        app.run(new String[]{"paste", "-m"});

        assertTrue("Prints to IO when failing to parse",
                io.out().contains("Invalid"));

        verifyStatic(Mockito.never());
        ExternalsUtil.getUserEditedMessage(anyString(), anyString(), anyBoolean());
    }

    @Test
    public void pasteRunsRightWithNoMessageSwitch() {
        when(TmcUtil.sendPaste(eq(ctx), any(Exercise.class), anyString()))
                .thenReturn(pasteUri);
        app.run(new String[] {"paste", "-n", "paste-exercise"});

        io.assertContains("Paste sent for exercise paste-exercise");
        assertTrue("Prints the paste URI",
                io.out().contains(pasteUri.toString()));

        verifyStatic(Mockito.never());
        ExternalsUtil.getUserEditedMessage(anyString(), anyString(), anyBoolean());

        verifyStatic(Mockito.times(1));
        TmcUtil.sendPaste(eq(ctx), eq(exercise),
                eq(""));
    }

    @Test
    public void handlesExceptionWhenCallableFails() {
        io.addConfirmationPrompt(true);
        when(TmcUtil.sendPaste(eq(ctx), any(Exercise.class), anyString()))
                .thenReturn(null);
        app.run(new String[] {"paste", "paste-exercise"});

        io.assertContains("Unable to send the paste");
        io.assertAllPromptsUsed();

        verifyStatic(Mockito.times(1));
        ExternalsUtil.getUserEditedMessage(anyString(), anyString(), anyBoolean());

        verifyStatic(Mockito.times(1));
        TmcUtil.sendPaste(eq(ctx), eq(exercise), anyString());
    }

    @Test
    public void failsWithNoExercise() {
        Mockito.when(workDir.getExerciseNames()).thenReturn(new ArrayList<String>());
        Mockito.when(workDir.addPath()).thenReturn(false);
        Mockito.when(workDir.addPath(anyString())).thenReturn(false);
        app.run(new String[] {"paste", "-m", "This is a message given as an argument"});

        io.assertContains("You are not in exercise directory.");

        verifyStatic(Mockito.never());
        TmcUtil.sendPaste(eq(ctx), any(Exercise.class), anyString());
    }
}
