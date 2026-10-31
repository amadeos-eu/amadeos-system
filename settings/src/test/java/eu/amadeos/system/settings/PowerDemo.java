package eu.amadeos.system.settings;

import eu.amadeos.system.base.power.BatteryStatus;
import eu.amadeos.system.base.ui.SessionDialogHelper;
import eu.amadeos.system.input.InputApplier;

import javax.swing.SwingUtilities;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/**
 * Opens the Settings at Power, or the Exit dialog, with stand-ins for logind and the battery, for pictures:
 * {@code PowerDemo [laptop|desktop|exit|exit-hibernate] [language]}.
 */
final class PowerDemo {
    private PowerDemo() {
    }

    public static void main(String[] args) throws Exception {
        String scene = args.length > 0 ? args[0] : "laptop";
        if (args.length > 1) {
            Locale locale = Locale.forLanguageTag(args[1]);
            SettingsTexts.use(locale);
            Locale.setDefault(locale);
        }
        if (scene.startsWith("exit")) {
            SessionDialogHelper.showSessionPowerDialog(null, new FakePower(scene.equals("exit-hibernate")));
            return;
        }
        Path home = Files.createTempDirectory("settings-demo");
        boolean laptop = scene.equals("laptop");
        SystemAccess fake = SystemPagesTest.fakeSystem();
        SystemAccess system = new SystemAccess(fake.keyring(), fake.packages(), fake.clock(), fake.launcher(),
                fake.applications(), fake.displays(), fake.proxyCheck(), fake.network(), fake.sound(), fake.soundTest(),
                fake.bluetooth(), fake.printers(), fake.users(), new FakePower(false),
                laptop ? FakePower::laptopBattery : Optional::<BatteryStatus>empty, fake.mimeApps(), fake.regions());
        SwingUtilities.invokeLater(() -> {
            SettingsWindow window = new SettingsWindow(SettingsStores.in(home), new InputApplier(), system);
            window.openControl(SettingsCategory.POWER);
            window.setSize(1000, 720);
            window.setVisible(true);
        });
    }
}
