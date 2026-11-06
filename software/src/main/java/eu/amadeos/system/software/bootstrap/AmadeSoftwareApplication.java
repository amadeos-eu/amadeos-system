package eu.amadeos.system.software.bootstrap;

import eu.amadeos.commons.app.AmadeApplication;
import eu.amadeos.commons.app.AppInfo;
import eu.amadeos.system.software.ui.MainWindow;

import java.util.List;
import java.util.Optional;

/**
 * {@code amadeos-software [--updates] [--settings] [--search=TEXT]}: {@code --updates} opens the Updates tab even
 * when nothing waits (what the front panel's updates cell starts), {@code --settings} the settings dialog as well,
 * {@code --search=TEXT} starts with that search (the Font Viewer's Get More Fonts passes {@code fonts-}).
 */
public final class AmadeSoftwareApplication {
    public static final AppInfo APP = new AppInfo("amadeos-software", "Software", "software");
    static final String SEARCH = "--search=";

    private AmadeSoftwareApplication() {
    }

    public static void main(String[] args) {
        List<String> options = List.of(args);
        AmadeApplication.launch(APP, injector -> {
            MainWindow window = injector.getInstance(MainWindow.class);
            window.setVisible(true);
            search(options).ifPresent(window::search);
            window.start(options.contains("--updates"), options.contains("--settings"));
        }, new SoftwareModule());
    }

    /** The text of the last {@code --search=TEXT}, if any and not blank. */
    static Optional<String> search(List<String> options) {
        return options.stream().filter(o -> o.startsWith(SEARCH)).map(o -> o.substring(SEARCH.length()).strip())
                .reduce((first, second) -> second).filter(s -> !s.isEmpty());
    }
}
