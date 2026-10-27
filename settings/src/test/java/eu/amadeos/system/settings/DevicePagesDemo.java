package eu.amadeos.system.settings;

import eu.amadeos.system.input.InputApplier;

import javax.swing.SwingUtilities;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Opens the Settings with the stand-in devices of {@link FakeDevices}, for pictures of the Network, Sound,
 * Bluetooth and Printers pages: {@code DevicePagesDemo <control> [language]}.
 */
final class DevicePagesDemo {
    private DevicePagesDemo() {
    }

    public static void main(String[] args) throws Exception {
        String control = args.length > 0 ? args[0] : "sound";
        if (args.length > 1) SettingsTexts.use(Locale.forLanguageTag(args[1]));
        Path home = Files.createTempDirectory("settings-demo");
        SwingUtilities.invokeLater(() -> {
            SettingsWindow window = new SettingsWindow(SettingsStores.in(home), new InputApplier(),
                    SystemPagesTest.fakeSystem());
            if (control.equals("proxy")) window.openProxy();
            else window.openControl(SettingsCategory.valueOf(control.toUpperCase(Locale.ROOT)));
            window.setSize(960, 720);
            window.setVisible(true);
        });
    }
}
