package eu.amadeos.system.widgets;

import eu.amadeos.commons.config.PropertiesFile;
import eu.amadeos.commons.widget.DesktopWidget;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * The widgets there are: the host's own, then those that applications announce. An application installs a
 * {@code .widget} file, for example {@code /usr/share/amadeos/widgets/amade-calendar.widget}:
 *
 * <pre>
 * # The jar with the widget classes and META-INF/services/eu.amadeos.commons.widget.DesktopWidget
 * jar=/opt/amade-calendar/lib/amade-calendar.jar
 * </pre>
 *
 * The host loads the jar with its own class loader as parent, so the widget uses the host's copy of the
 * widget API. {@code ~/.local/share/amadeos/widgets/} is read too, for widgets under development. When two
 * widgets have the same id, the first one found wins.
 */
public final class WidgetCatalog {
    private static final Logger LOGGER = Logger.getLogger(WidgetCatalog.class.getName());
    public static final Path SYSTEM_DIR = Path.of("/usr/share/amadeos/widgets");

    private final Map<String, DesktopWidget> widgets = new LinkedHashMap<>();

    /** The host's own widgets and those of the {@code .widget} files in {@code dirs}. */
    public static WidgetCatalog load(List<Path> dirs) {
        WidgetCatalog catalog = new WidgetCatalog();
        catalog.addAll(ServiceLoader.load(DesktopWidget.class, WidgetCatalog.class.getClassLoader()));
        for (Path dir : dirs) {
            for (Path file : widgetFiles(dir)) catalog.addFile(file);
        }
        return catalog;
    }

    public static List<Path> defaultDirs() {
        return List.of(SYSTEM_DIR, Path.of(System.getProperty("user.home"), ".local/share/amadeos/widgets"));
    }

    static List<Path> widgetFiles(Path dir) {
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.getFileName().toString().endsWith(".widget")).sorted().toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    void addFile(Path file) {
        Properties p;
        try {
            p = PropertiesFile.load(file);
        } catch (RuntimeException e) {
            LOGGER.warning(() -> "Cannot read " + file + ": " + e.getMessage());
            return;
        }
        String jar = p.getProperty("jar", "").strip();
        if (jar.isEmpty()) {
            LOGGER.warning(() -> file + " names no jar");
            return;
        }
        Path jarPath = file.getParent().resolve(jar);
        if (!Files.isRegularFile(jarPath)) {
            LOGGER.warning(() -> file + ": " + jarPath + " is missing");
            return;
        }
        try {
            URLClassLoader loader = new URLClassLoader("widget:" + file.getFileName(),
                    new URL[]{jarPath.toUri().toURL()}, WidgetCatalog.class.getClassLoader());
            addAll(ServiceLoader.load(DesktopWidget.class, loader));
        } catch (IOException e) {
            LOGGER.warning(() -> "Cannot load " + jarPath + ": " + e.getMessage());
        }
    }

    private void addAll(ServiceLoader<DesktopWidget> loader) {
        var it = loader.iterator();
        while (true) {
            try {
                if (!it.hasNext()) break;
                add(it.next());
            } catch (ServiceConfigurationError | LinkageError e) {
                LOGGER.log(Level.WARNING, "A widget cannot be loaded", e);
            }
        }
    }

    void add(DesktopWidget widget) {
        widgets.putIfAbsent(widget.id(), widget);
    }

    public Optional<DesktopWidget> find(String id) {
        return Optional.ofNullable(widgets.get(id));
    }

    /** All widgets, by name, for the gallery. */
    public List<DesktopWidget> all() {
        List<DesktopWidget> list = new ArrayList<>(widgets.values());
        list.sort(Comparator.comparing(w -> w.name().toLowerCase(Locale.ROOT)));
        return list;
    }
}
