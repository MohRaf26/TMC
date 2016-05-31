package fi.helsinki.cs.tmc.cli.command;

import fi.helsinki.cs.tmc.cli.Application;
import fi.helsinki.cs.tmc.cli.command.core.Command;
import fi.helsinki.cs.tmc.cli.command.core.CommandInterface;
import fi.helsinki.cs.tmc.cli.tmcstuff.TmcUtil;

import fi.helsinki.cs.tmc.core.TmcCore;
import fi.helsinki.cs.tmc.core.domain.Course;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Command for listing all available courses to user.
 */
@Command(name = "list-courses", desc = "List the available courses.")
public class ListCoursesCommand implements CommandInterface {
    private static final Logger logger = LoggerFactory.getLogger(ListCoursesCommand.class);
    private Application app;

    public ListCoursesCommand(Application app) {
        this.app = app;
    }

    @Override
    public void run(String[] args) {
        List<Course> courses;
        TmcCore core;

        core = this.app.getTmcCore();
        if (core == null) {
            return;
        }
        courses = TmcUtil.listCourses(core);

        for (Course course : courses) {
            System.out.println(course.getName());
        }
    }
}
