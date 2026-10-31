package eu.amadeos.system.settings;

import eu.amadeos.system.users.Account;
import eu.amadeos.system.users.UserGroup;
import eu.amadeos.system.users.UserService.UserException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.swing.JCheckBox;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** The Users and Groups page with the family computer of {@link FakeUsers}. */
class UsersPageTest {

    @BeforeAll
    static void english() {
        SettingsTexts.use(Locale.ENGLISH);
    }

    @Test
    void usersSitInTheSystemGroupAndActAtOnce() {
        assertEquals(SettingsCategory.Group.SYSTEM, SettingsCategory.USERS.group());
        assertTrue(SettingsCategory.USERS.immediate());
        assertTrue(SettingsCategory.USERS.matches("sudo"));
        assertTrue(SettingsCategory.USERS.matches("account"));
    }

    @Test
    void textsOfAccountsAndGroups() {
        Account anna = new Account("anna", 1001, "Anna Eichinger", "/home/anna", "/bin/bash", Set.of("sudo"), false,
                true);
        assertEquals("<html><b>Anna Eichinger</b><br>anna · Administrator · logged in</html>",
                UsersPage.describe(anna, "ralf"));
        assertEquals("<html><b>Anna Eichinger</b><br>anna · Administrator · you</html>", UsersPage.describe(anna, "anna"));
        UserGroup printers = new UserGroup("lpadmin", 120, List.of("ralf", "anna"), UserGroup.Kind.PERMISSION);
        assertEquals("<html><b>Manage printers</b><br>lpadmin · 2 members</html>", UsersPage.describe(printers));
        assertEquals("family", UsersPage.groupLabel(new UserGroup("family", 1010, List.of(), UserGroup.Kind.OWN)));
        assertEquals("Not possible: the computer needs at least one administrator", UsersPage.message(
                new UserException(UserException.Reason.REFUSED, "last-admin", "ralf is the last administrator")));
        assertEquals("Nothing changed: the password prompt was closed",
                UsersPage.message(new UserException(UserException.Reason.DISMISSED, "dismissed", "")));
    }

    @Test
    void newAccountsAreChecked() {
        List<String> taken = List.of("ralf");
        assertNull(UsersPage.addProblem("Tom", "tom", taken, "x1".toCharArray(), "x1".toCharArray()));
        assertEquals("The login name ralf is taken.",
                UsersPage.addProblem("Ralf", "ralf", taken, "x".toCharArray(), "x".toCharArray()));
        assertEquals("The two passwords are not the same.",
                UsersPage.addProblem("Tom", "tom", taken, "a".toCharArray(), "b".toCharArray()));
        assertEquals("The password is empty.",
                UsersPage.addProblem("Tom", "tom", taken, new char[0], new char[0]));
        assertTrue(UsersPage.addProblem("Tom", "Tom", taken, "a".toCharArray(), "a".toCharArray())
                .startsWith("The login name starts"));
        assertTrue(UsersPage.addProblem("Tom: boss", "tom", taken, "a".toCharArray(), "a".toCharArray())
                .startsWith("The full name"));
    }

    @Test
    void pageListsAccountsAndChangesTheType() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        FakeUsers users = new FakeUsers();
        List<String> status = new ArrayList<>();
        AtomicReference<UsersPage> page = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> page.set(new UsersPage(status::add, users)));
        waitFor(() -> page.get().shownAccounts().size() == 5);
        SwingUtilities.invokeAndWait(() -> {
            UsersPage p = page.get();
            assertEquals("Anna Eichinger", p.accountList().getModel().getElementAt(0).fullName(), "sorted by name");
            assertEquals("ralf", p.accountList().getSelectedValue().login(), "starts on the own account");
            assertFalse(p.removeButton().isEnabled(), "you cannot remove yourself");
            assertFalse(p.disabledBox().isEnabled());
            p.select("tom");
            assertTrue(p.removeButton().isEnabled());
            assertEquals(0, p.accountType().getSelectedIndex());
            p.accountType().setSelectedIndex(1);
        });
        waitFor(() -> users.calls.contains("admin tom true"));
        waitFor(() -> status.contains("Tom Eichinger is now an administrator"));
        waitFor(() -> page.get().accountList().getSelectedValue().admin());

        // A refused change leaves the account as it was and says why.
        users.failNext = new UserException(UserException.Reason.REFUSED, "last-admin", "tom is the last administrator");
        SwingUtilities.invokeAndWait(() -> page.get().accountType().setSelectedIndex(0));
        waitFor(() -> status.contains("Not possible: the computer needs at least one administrator"));
        waitFor(() -> page.get().accountType().getSelectedIndex() == 1);
    }

    @Test
    void groupsTabTogglesMembers() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        FakeUsers users = new FakeUsers();
        AtomicReference<UsersPage> page = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> page.set(new UsersPage(s -> { }, users)));
        waitFor(() -> page.get().shownAccounts().size() == 5);
        SwingUtilities.invokeAndWait(() -> {
            page.get().selectGroup("lpadmin");
            JCheckBox tom = (JCheckBox) page.get().memberBoxes().getComponent(4);
            assertEquals("Tom Eichinger (tom)", tom.getText());
            assertFalse(tom.isSelected());
            tom.doClick();
        });
        waitFor(() -> users.calls.contains("join tom lpadmin"));
        waitFor(() -> users.group.get("lpadmin").contains("tom"));
    }

    private static void waitFor(BooleanSupplier condition) throws Exception {
        for (int i = 0; i < 100; i++) {
            AtomicReference<Boolean> ok = new AtomicReference<>(false);
            SwingUtilities.invokeAndWait(() -> ok.set(condition.getAsBoolean()));
            if (ok.get()) return;
            Thread.sleep(50);
        }
        throw new AssertionError("condition not met in time");
    }
}
