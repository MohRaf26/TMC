package fi.helsinki.cs.tmc.cli.command;

import fi.helsinki.cs.tmc.cli.backend.SettingsIo;
import fi.helsinki.cs.tmc.cli.backend.TmcUtil;
import fi.helsinki.cs.tmc.cli.core.AbstractCommand;
import fi.helsinki.cs.tmc.cli.core.CliContext;
import fi.helsinki.cs.tmc.cli.core.Command;
import fi.helsinki.cs.tmc.cli.io.EnvironmentUtil;
import fi.helsinki.cs.tmc.cli.io.Io;
import fi.helsinki.cs.tmc.cli.utils.OptionalToGoptional;
import fi.helsinki.cs.tmc.core.domain.Organization;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import com.google.common.base.Optional;

@Command(name = "organization", desc = "Change organization")
public class OrganizationCommand extends AbstractCommand {

    private CliContext ctx;
    private Io io;

    @Override
    public void getOptions(Options options) {
        options.addOption("o", "organization", true, "Slug of organization");
    }

    @Override
    public void run(CliContext ctx, CommandLine args) {
        this.ctx = ctx;
        if (!this.ctx.checkIsLoggedIn(false)) {
            return;
        }

        this.ctx.getAnalyticsFacade().saveAnalytics("organization");

        Optional<Organization> organization = chooseOrganization(ctx, args);
        this.ctx.getSettings().setOrganization(organization);
        SettingsIo.saveCurrentSettingsToAccountList(this.ctx.getSettings());
    }

    private List<Organization> listOrganizations() {
        if (this.ctx.getSettings().getServerAddress().isEmpty()) {
            io.println("A server address has to be specified to get organizations");
            return null;
        }
        List<Organization> organizations = TmcUtil.getOrganizationsFromServer(ctx);
        return organizations;
    }

    private void printOrganizations(List<Organization> organizations, boolean pager) {
        if (organizations.isEmpty()) {
            io.print("No organizations found.");
            return;
        }
        List<Organization> pinned = new ArrayList();
        List<Organization> others = new ArrayList();
        organizations.stream()
                .sorted(Comparator.comparing(Organization::getName))
                .forEach(o -> {
                    if (o.isPinned()) {
                        pinned.add(o);
                    } else {
                        others.add(o);
                    }
                });
        io.println("Available Organizations:");
        io.println();
        pinned.forEach(this::printFormattedOrganization);
        io.println("----------");
        others.forEach(this::printFormattedOrganization);
        io.println();
    }

    private void printFormattedOrganization(Organization o) {
        io.println(o.getName() + " (slug: " + o.getSlug() + ")");
    }


    private String getOrganizationFromUser(List<Organization> organizations, CommandLine line, boolean printOptions, boolean oneLine) {
        if (oneLine && line.hasOption("o")) {
            return line.getOptionValue("o");
        }
        if (printOptions) {
            printOrganizations(organizations, !line.hasOption("n") && !EnvironmentUtil.isWindows());
        }
        return io.readLine("Choose organization by writing its slug: ");
    }


    public Optional<Organization> chooseOrganization(CliContext ctx, CommandLine args) {
        this.ctx = ctx;
        this.io = ctx.getIo();
        boolean printOptions = true;
        boolean oneLine = args.hasOption("o");
        List<Organization> organizations = listOrganizations();
        if (organizations == null) {
            io.errorln("Failed to fetch organizations from server.");
            return Optional.absent();
        }
        java.util.Optional<Organization> organization;
        while (true) {
            String slug = getOrganizationFromUser(organizations, args, printOptions, oneLine);
            printOptions = false;
            organization = organizations.stream().filter(o -> o.getSlug().equals(slug.trim().toLowerCase())).findFirst();
            if (organization.isPresent()) {
                break;
            } else {
                io.errorln("Slug doesn't match any organization.");
                if (oneLine) {
                    oneLine = false;
                    printOptions = true;
                }
            }
        }
        io.println("Choosing organization " + organization.get().getName());
        return OptionalToGoptional.convert(organization);
    }

}