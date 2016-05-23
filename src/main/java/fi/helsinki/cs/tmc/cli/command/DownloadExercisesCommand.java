package fi.helsinki.cs.tmc.cli.command;

import fi.helsinki.cs.tmc.cli.Application;
import fi.helsinki.cs.tmc.core.TmcCore;
import fi.helsinki.cs.tmc.core.domain.Course;
import fi.helsinki.cs.tmc.core.domain.Exercise;
import fi.helsinki.cs.tmc.core.domain.ProgressObserver;

import java.util.List;
import java.util.concurrent.Callable;

public class DownloadExercisesCommand implements Command {
    private Application app;

    public DownloadExercisesCommand(Application app) {
        this.app = app;
    }

    @Override
    public String getDescription() {
        return "Download exercises for a specific course";
    }

    @Override
    public String getName() {
        return "download";
    }

    @Override
    public void run(String[] args) {
        Callable<List<Course>> callable;
        List<Course> courses;
        TmcCore core;
        Course course = null;
        if (args.length == 0) {
            return;
        }
        core = this.app.getTmcCore();
        callable = core.listCourses(ProgressObserver.NULL_OBSERVER);

        try {
            courses = callable.call();
        } catch (Exception e) {
            return;
        }

        for (Course item : courses) {
            if (item.getName().equals(args[0])) {
                course = item;
            }
        }

        try {
            course = core.getCourseDetails(ProgressObserver.NULL_OBSERVER, course).call();
        } catch (Exception e) {
            System.out.println("Course not found.");
            return;
        }
        List<Exercise> exercises = course.getExercises();
        List<Exercise> downloaded;
        try {
            downloaded = core.downloadOrUpdateExercises(ProgressObserver.NULL_OBSERVER, exercises)
                    .call();
        } catch (Exception e) {
            return;
        }
        System.out.println(downloaded);
    }
}