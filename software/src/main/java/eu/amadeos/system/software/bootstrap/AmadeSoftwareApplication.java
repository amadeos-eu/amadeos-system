package eu.amadeos.system.software.bootstrap;

import eu.amadeos.commons.app.AmadeApplication;
import eu.amadeos.commons.app.AppInfo;
import eu.amadeos.system.software.ui.MainWindow;

import java.util.List;

/**
 * {@code amadeos-software [--updates] [--settings]}: {@code --updates} opens the Updates tab even when nothing
 * waits (what the front panel's updates cell starts), {@code --settings} the settings dialog as well.
 */
public final class AmadeSoftwareApplication {
    public static final AppInfo APP = new AppInfo("amadeos-software", "Software", "software");

    private AmadeSoftwareApplication() {
    }

    public static void main(String[] args) {
        List<String> options = List.of(args);
        AmadeApplication.launch(APP, injector -> {
            MainWindow window = injector.getInstance(MainWindow.class);
            window.setVisible(true);
            window.start(options.contains("--updates"), options.contains("--settings"));
        }, new SoftwareModule());
    }
}
