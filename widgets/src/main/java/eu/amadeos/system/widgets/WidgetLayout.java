package eu.amadeos.system.widgets;

import eu.amadeos.commons.config.PropertiesFile;

import java.awt.Rectangle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * The widgets on the desktop and where they are, in {@code ~/.config/amadeos/widgets.conf}:
 *
 * <pre>
 * placed=clock-1,note-1
 * clock-1.type=clock
 * clock-1.bounds=1180,60,220,220
 * </pre>
 *
 * The settings of each copy are in {@code ~/.config/amadeos/widgets/<copy>.conf}.
 */
public final class WidgetLayout {
    public static final String FILE_NAME = "widgets.conf";

    /** One widget on the desktop. */
    public record Placement(String id, String type, Rectangle bounds) {
        public Placement {
            bounds = new Rectangle(bounds);
        }
    }

    private final Path file;
    private final List<Placement> placed = new ArrayList<>();

    private WidgetLayout(Path file) {
        this.file = file;
    }

    public static WidgetLayout load(Path file) {
        WidgetLayout layout = new WidgetLayout(file);
        Properties p;
        try {
            p = Files.exists(file) ? PropertiesFile.load(file) : new Properties();
        } catch (RuntimeException e) {
            p = new Properties();
        }
        for (String id : Arrays.stream(p.getProperty("placed", "").split(",")).map(String::strip)
                .filter(s -> !s.isEmpty()).toList()) {
            String type = p.getProperty(id + ".type", "").strip();
            Rectangle r = parse(p.getProperty(id + ".bounds", ""));
            if (!type.isEmpty() && r != null) layout.placed.add(new Placement(id, type, r));
        }
        return layout;
    }

    static Rectangle parse(String text) {
        String[] parts = text.split(",");
        if (parts.length != 4) return null;
        try {
            int[] v = Arrays.stream(parts).mapToInt(s -> Integer.parseInt(s.strip())).toArray();
            return v[2] > 0 && v[3] > 0 ? new Rectangle(v[0], v[1], v[2], v[3]) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public List<Placement> placed() {
        return List.copyOf(placed);
    }

    /** A new copy of {@code type} at {@code bounds}; its id is the type and the next free number. */
    public Placement add(String type, Rectangle bounds) {
        int n = 1;
        while (contains(type + "-" + n)) n++;
        Placement p = new Placement(type + "-" + n, type, bounds);
        placed.add(p);
        save();
        return p;
    }

    private boolean contains(String id) {
        return placed.stream().anyMatch(p -> p.id().equals(id));
    }

    public void move(String id, Rectangle bounds) {
        for (int i = 0; i < placed.size(); i++) {
            Placement p = placed.get(i);
            if (p.id().equals(id)) placed.set(i, new Placement(id, p.type(), bounds));
        }
        save();
    }

    public void remove(String id) {
        placed.removeIf(p -> p.id().equals(id));
        save();
        try {
            Files.deleteIfExists(settingsFile(file, id));
        } catch (java.io.IOException e) {
            // stays behind, harmless
        }
    }

    /** Where the settings of a copy are kept: next to widgets.conf, in the folder widgets. */
    public static Path settingsFile(Path layoutFile, String id) {
        return layoutFile.resolveSibling("widgets").resolve(id + ".conf");
    }

    public Path file() {
        return file;
    }

    /**
     * A place for a new widget of the given size: in a column along the right edge of the work area, below
     * the widgets already there; when the column is full, the next column to the left.
     */
    public Rectangle freeSpot(Rectangle work, int width, int height) {
        int gap = 16;
        int x = work.x + work.width - width - gap;
        while (x >= work.x) {
            int y = work.y + gap;
            while (y + height <= work.y + work.height) {
                Rectangle candidate = new Rectangle(x, y, width, height);
                Rectangle grown = new Rectangle(x - gap / 2, y - gap / 2, width + gap, height + gap);
                Placement hit = placed.stream().filter(p -> p.bounds().intersects(grown)).findFirst().orElse(null);
                if (hit == null) return candidate;
                y = hit.bounds().y + hit.bounds().height + gap;
            }
            x -= width + gap;
        }
        return new Rectangle(work.x + gap, work.y + gap, width, height);
    }

    void save() {
        Properties p = new Properties();
        p.setProperty("placed", String.join(",", placed.stream().map(Placement::id).toList()));
        for (Placement pl : placed) {
            Rectangle r = pl.bounds();
            p.setProperty(pl.id() + ".type", pl.type());
            p.setProperty(pl.id() + ".bounds", r.x + "," + r.y + "," + r.width + "," + r.height);
        }
        PropertiesFile.store(file, p, "amadeOS desktop widgets (amadeos-widgets)");
    }
}
