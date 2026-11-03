package eu.amadeos.system.settings;

import eu.amadeos.system.input.InputApplier;

import javax.swing.SwingUtilities;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Opens the Settings at Keyboard Shortcuts or Accessibility with stand-ins for the system, for pictures:
 * {@code KeysDemo [shortcuts|accessibility] [language]}. The settings files go to a new temporary directory, or
 * to the directory in the system property {@code demo.home}.
 */
final class KeysDemo {
    private KeysDemo() {
    }

    public static void main(String[] args) throws Exception {
        String scene = args.length > 0 ? args[0] : "shortcuts";
        if (args.length > 1) {
            Locale locale = Locale.forLanguageTag(args[1]);
            SettingsTexts.use(locale);
            Locale.setDefault(locale);
        }
        String given = System.getProperty("demo.home");
        Path home = given != null ? Path.of(given) : Files.createTempDirectory("settings-demo");
        SettingsCategory category = scene.equals("accessibility") ? SettingsCategory.ACCESSIBILITY : SettingsCategory.SHORTCUTS;
        SwingUtilities.invokeLater(() -> {
            SettingsWindow window = new SettingsWindow(SettingsStores.in(home), new InputApplier(),
                    SystemPagesTest.fakeSystem());
            window.openControl(category);
            window.setSize(960, 700);
            window.setVisible(true);
        });
    }
}
