package eu.amadeos.system.settings;

import eu.amadeos.commons.i18n.Language;
import eu.amadeos.commons.i18n.LocaleStore;
import eu.amadeos.commons.i18n.RegionalFormats;
import eu.amadeos.system.input.InputApplier;

import javax.swing.SwingUtilities;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/**
 * Pictures of Default Applications and Date and Time for the handbooks:
 * {@code DateAppsDemo applications|datetime|manual|formats [language]}.
 */
final class DateAppsDemo {
    private DateAppsDemo() {
    }

    public static void main(String[] args) throws Exception {
        String scene = args.length > 0 ? args[0] : "datetime";
        Locale locale = args.length > 1 ? Locale.forLanguageTag(args[1]) : Locale.ENGLISH;
        SettingsTexts.use(locale);
        Path home = Files.createTempDirectory("settings-demo");
        Language.of(locale).filter(l -> l != Language.ENGLISH).ifPresent(l -> {
            try {
                new LocaleStore(home).write(l);
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        });
        if (scene.equals("formats")) new RegionalFormats(home).write(Optional.of(Locale.of("en", "GB")));
        SystemPagesTest.FakeClock clock = new SystemPagesTest.FakeClock();
        if (scene.equals("manual")) clock.status = new SystemClock.Status("Europe/Berlin", true, false, false);
        SystemAccess fake = SystemPagesTest.fakeSystem();
        Path apps = Files.createTempDirectory("mimeapps");
        SystemAccess system = new SystemAccess(fake.keyring(), fake.packages(), clock, fake.launcher(),
                () -> FakeApplications.ALL, fake.displays(), fake.proxyCheck(), fake.network(), fake.sound(),
                fake.soundTest(), fake.bluetooth(), fake.printers(), fake.users(), fake.power(), fake.battery(),
                () -> SystemPagesTest.mimeApps(apps), () -> RegionalFormats.REGIONS);
        SwingUtilities.invokeLater(() -> {
            SettingsWindow window = new SettingsWindow(SettingsStores.in(home), new InputApplier(), system);
            window.openControl(scene.equals("applications") ? SettingsCategory.APPLICATIONS : SettingsCategory.DATETIME);
            window.setSize(1000, 760);
            window.setVisible(true);
            if (scene.equals("applications")) {
                ApplicationsPage page = (ApplicationsPage) window.page(SettingsCategory.APPLICATIONS);
                page.choice(ApplicationsPage.Role.DOCUMENTS).setSelectedItem("libreoffice-writer.desktop");
            }
        });
    }
}
