package eu.amadeos.system.settings;

import eu.amadeos.system.input.InputApplier;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Opens the Settings at Users and Groups with the family computer of {@link FakeUsers}, for pictures:
 * {@code UsersDemo [users|tom|groups|add|groups-dialog|remove|password] [language]}.
 */
final class UsersDemo {
    private UsersDemo() {
    }

    public static void main(String[] args) throws Exception {
        String scene = args.length > 0 ? args[0] : "users";
        if (args.length > 1) SettingsTexts.use(Locale.forLanguageTag(args[1]));
        Path home = Files.createTempDirectory("settings-demo");
        SwingUtilities.invokeLater(() -> {
            SettingsWindow window = new SettingsWindow(SettingsStores.in(home), new InputApplier(),
                    SystemPagesTest.fakeSystem());
            window.openControl(SettingsCategory.USERS);
            window.setSize(1000, 720);
            window.setVisible(true);
            UsersPage page = (UsersPage) window.page(SettingsCategory.USERS);
            Timer later = new Timer(1500, e -> {
                switch (scene) {
                    case "tom" -> page.select("tom");
                    case "groups" -> {
                        page.tabs().setSelectedIndex(1);
                        page.selectGroup("lpadmin");
                    }
                    case "add" -> page.askAdd();
                    case "groups-dialog" -> {
                        page.select("tom");
                        page.askGroups(page.accountList().getSelectedValue());
                    }
                    case "remove" -> {
                        page.select("tom");
                        page.askRemove(page.accountList().getSelectedValue());
                    }
                    case "password" -> page.askPassword(page.accountList().getSelectedValue());
                    default -> { }
                }
            });
            later.setRepeats(false);
            later.start();
        });
    }
}
