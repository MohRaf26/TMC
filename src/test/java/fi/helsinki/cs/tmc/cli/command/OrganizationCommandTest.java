package fi.helsinki.cs.tmc.cli.command;

import fi.helsinki.cs.tmc.cli.Application;
import fi.helsinki.cs.tmc.cli.backend.AccountList;
import fi.helsinki.cs.tmc.cli.backend.Settings;
import fi.helsinki.cs.tmc.cli.backend.SettingsIo;
import fi.helsinki.cs.tmc.cli.backend.TmcUtil;
import fi.helsinki.cs.tmc.cli.core.CliContext;
import fi.helsinki.cs.tmc.cli.io.TestIo;
import fi.helsinki.cs.tmc.core.TmcCore;
import fi.helsinki.cs.tmc.core.domain.Organization;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;
import java.util.List;
import com.google.common.base.Optional;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.verifyStatic;

@RunWith(PowerMockRunner.class)
@PrepareForTest({SettingsIo.class, TmcUtil.class})
public class OrganizationCommandTest {
    private static List<Organization> organizationList;
    private static final Organization TEST_ORGANIZATION = new Organization("test", "test", "test", "test", false);
    private static List<Organization> mixedOrganizationList;
    private static OrganizationCommand command;
    private static Settings settings;

    private Application app;
    private CliContext ctx;
    private TestIo io;
    private TmcCore mockCore;

    @Before
    public void setUp() {
        io = new TestIo();
        mockCore = mock(TmcCore.class);
        organizationList = new ArrayList<>();
        organizationList.add(TEST_ORGANIZATION);
        mixedOrganizationList = new ArrayList<>();
        mixedOrganizationList.add(new Organization("A", "", "a", "", true));
        mixedOrganizationList.add(new Organization("B", "", "b", "", true));
        mixedOrganizationList.add(new Organization("C", "", "c", "", false));
        mixedOrganizationList.add(new Organization("D", "", "d", "", false));
        mixedOrganizationList.add(new Organization("E", "", "e", "", false));

        command = new OrganizationCommand();

        ctx = spy(new CliContext(io, mockCore));
        settings = ctx.getSettings();
        app = new Application(ctx);

        mockStatic(TmcUtil.class);
        mockStatic(SettingsIo.class);
        when(TmcUtil.hasConnection(eq(ctx))).thenReturn(true);
        when(SettingsIo.loadAccountList()).thenReturn(new AccountList());
        when(SettingsIo.saveAccountList(any(AccountList.class))).thenReturn(true);
    }

    @Test
    public void printsOrganizationsPinnedFirst() {
        when(TmcUtil.getOrganizationsFromServer(any(CliContext.class))).thenReturn(mixedOrganizationList);
        String[] args = {"organization"};
        io.addLinePrompt("a");
        app.run(args);
        io.assertContains("A (slug: a)\nB (slug: b)");
        io.assertContains("C (slug: c)\nD (slug: d)\nE (slug: e)");
    }

    @Test
    public void asksSlugUntilInputMatchesOrganization() {
        when(TmcUtil.getOrganizationsFromServer(any(CliContext.class))).thenReturn(organizationList);
        String[] args = {"organization"};
        io.addLinePrompt("unmatched slug");
        io.addLinePrompt(organizationList.get(0).getSlug());
        app.run(args);
        io.assertContains("doesn't match");
    }

    @Test
    public void runningCommandSetsOrganizationToSettings() {
        when(TmcUtil.getOrganizationsFromServer(any(CliContext.class))).thenReturn(organizationList);
        String[] args = {"organization"};
        io.addLinePrompt(TEST_ORGANIZATION.getSlug());
        app.run(args);
        assertEquals(settings.getOrganization().get().getName(), TEST_ORGANIZATION.getName());
        assertEquals(settings.getOrganization().get().getSlug(), TEST_ORGANIZATION.getSlug());
        assertEquals(settings.getOrganization().get().getInformation(), TEST_ORGANIZATION.getInformation());
    }

    @Test
    public void worksUsingOneLinerCommand() {
        when(TmcUtil.getOrganizationsFromServer(any(CliContext.class))).thenReturn(organizationList);
        String[] args = {"organization", "-o", TEST_ORGANIZATION.getSlug()};
        app.run(args);
        io.assertContains("Choosing organization " + TEST_ORGANIZATION.getName());
        assertEquals(settings.getOrganization().get().getName(), TEST_ORGANIZATION.getName());
        assertEquals(settings.getOrganization().get().getSlug(), TEST_ORGANIZATION.getSlug());
        assertEquals(settings.getOrganization().get().getInformation(), TEST_ORGANIZATION.getInformation());
        io.assertAllPromptsUsed();
    }

    @Test
    public void printsAllOrganizationsIfInvalidSlugGivenAsArgumentToOneLiner() {
        when(TmcUtil.getOrganizationsFromServer(any(CliContext.class))).thenReturn(organizationList);
        String[] args = {"organization",  "-o", "invalid"};
        io.addLinePrompt(organizationList.get(0).getSlug());
        app.run(args);
        io.assertContains(TEST_ORGANIZATION.getName());
    }

    @Test
    public void saveConfigruationsToAccountList() {
        when(TmcUtil.getOrganizationsFromServer(any(CliContext.class))).thenReturn(organizationList);
        String[] args = {"organization", "-o", TEST_ORGANIZATION.getSlug()};
        app.run(args);
        verifyStatic();
        SettingsIo.saveCurrentSettingsToAccountList(eq(settings));
    }
}
