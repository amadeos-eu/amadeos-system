package eu.amadeos.system.widgets;

import eu.amadeos.commons.config.PropertiesFile;
import eu.amadeos.commons.widget.WidgetColors;
import eu.amadeos.commons.widget.WidgetContext;
import eu.amadeos.system.base.process.ProcessLauncher;
import eu.amadeos.system.base.theme.ThemeManager;

import javax.swing.Timer;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * The context of one widget copy on the desktop: its settings file, its timers and theme listeners, which
 * {@link #close()} ends when the copy is removed.
 */
final class HostContext implements WidgetContext {
    private static final Logger LOGGER = Logger.getLogger(HostContext.class.getName());

    private final Path settingsFile;
    private final Properties settings;
    private final List<Timer> timers = new ArrayList<>();
    private final List<Runnable> themeTasks = new ArrayList<>();

    HostContext(Path settingsFile) {
        this.settingsFile = settingsFile;
        Properties p;
        try {
            p = PropertiesFile.load(settingsFile);
        } catch (RuntimeException e) {
            p = new Properties();
        }
        this.settings = p;
    }

    @Override
    public WidgetColors colors() {
        return WidgetColors.of(ThemeManager.currentTheme());
    }

    @Override
    public Locale locale() {
        return WidgetTexts.locale();
    }

    @Override
    public String get(String key, String fallback) {
        return settings.getProperty(key, fallback);
    }

    @Override
    public void set(String key, String value) {
        if (value == null) settings.remove(key);
        else settings.setProperty(key, value);
        PropertiesFile.store(settingsFile, settings, "amadeOS desktop widget");
    }

    @Override
    public void every(Duration period, Runnable task) {
        task.run();
        Timer t = new Timer((int) Math.max(50, Math.min(Integer.MAX_VALUE, period.toMillis())), e -> task.run());
        t.start();
        timers.add(t);
    }

    @Override
    public void onThemeChange(Runnable task) {
        themeTasks.add(task);
    }

    /** Called by the host when the desktop colours change. */
    void themeChanged() {
        for (Runnable task : themeTasks) {
            try {
                task.run();
            } catch (RuntimeException e) {
                LOGGER.warning(() -> "A widget failed on the new colours: " + e);
            }
        }
    }

    @Override
    public void open(String... command) {
        try {
            ProcessLauncher.start(List.of(command));
        } catch (Exception e) {
            LOGGER.warning(() -> "Cannot start " + String.join(" ", command) + ": " + e.getMessage());
        }
    }

    void close() {
        timers.forEach(Timer::stop);
        timers.clear();
        themeTasks.clear();
    }

}
