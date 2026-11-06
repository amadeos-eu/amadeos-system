package eu.amadeos.system.widgets;

import eu.amadeos.commons.widget.DesktopWidget;
import eu.amadeos.commons.widget.WidgetColors;
import eu.amadeos.commons.widget.WidgetContext;
import eu.amadeos.system.widgets.builtin.ClockWidget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import static org.junit.jupiter.api.Assertions.*;

class WidgetsTest {
    @TempDir
    Path dir;

    @Test
    void theLayoutKeepsPlacesAndGivesNewCopiesTheirOwnName() {
        Path file = dir.resolve("widgets.conf");
        WidgetLayout layout = WidgetLayout.load(file);
        WidgetLayout.Placement a = layout.add("clock", new Rectangle(10, 20, 200, 220));
        WidgetLayout.Placement b = layout.add("clock", new Rectangle(10, 260, 200, 220));
        assertEquals("clock-1", a.id());
        assertEquals("clock-2", b.id());
        layout.move("clock-1", new Rectangle(50, 60, 300, 300));

        WidgetLayout again = WidgetLayout.load(file);
        assertEquals(List.of("clock-1", "clock-2"), again.placed().stream().map(WidgetLayout.Placement::id).toList());
        assertEquals(new Rectangle(50, 60, 300, 300), again.placed().get(0).bounds());
        again.remove("clock-1");
        assertEquals(List.of("clock-2"), WidgetLayout.load(file).placed().stream().map(WidgetLayout.Placement::id).toList());
        assertEquals(dir.resolve("widgets/clock-2.conf"), WidgetLayout.settingsFile(file, "clock-2"));
    }

    @Test
    void brokenLinesAreLeftOut() throws Exception {
        Path file = dir.resolve("widgets.conf");
        Files.writeString(file, "placed=a,b,c\na.type=clock\na.bounds=1,2,3,4\nb.type=clock\nb.bounds=x\nc.bounds=1,2,3,4\n");
        assertEquals(List.of("a"), WidgetLayout.load(file).placed().stream().map(WidgetLayout.Placement::id).toList());
    }

    @Test
    void newWidgetsGoDownTheRightEdgeThenTheNextColumn() {
        WidgetLayout layout = WidgetLayout.load(dir.resolve("widgets.conf"));
        Rectangle work = new Rectangle(0, 30, 1000, 600);
        Rectangle first = layout.freeSpot(work, 200, 250);
        assertEquals(new Rectangle(784, 46, 200, 250), first);
        layout.add("clock", first);
        Rectangle second = layout.freeSpot(work, 200, 250);
        assertEquals(784, second.x);
        assertEquals(46 + 250 + 16, second.y);
        layout.add("clock", second);
        Rectangle third = layout.freeSpot(work, 200, 250);
        assertEquals(784 - 216, third.x, "the column is full");
    }

    @Test
    void theHostsOwnWidgetsAreFoundAndAWidgetFileWithoutJarIsSkipped() throws Exception {
        Files.writeString(dir.resolve("broken.widget"), "jar=missing.jar\n");
        Files.writeString(dir.resolve("empty.widget"), "\n");
        WidgetCatalog catalog = WidgetCatalog.load(List.of(dir, dir.resolve("absent")));
        assertTrue(catalog.find("clock").isPresent());
        assertTrue(catalog.find("photo").isPresent());
        assertTrue(catalog.find("note").isPresent());
        assertEquals(3, catalog.all().size());
    }

    @Test
    void theClockPaintsBothFaces() {
        Map<String, String> settings = new HashMap<>();
        WidgetContext context = new WidgetContext() {
            public WidgetColors colors() {
                return new WidgetColors(Color.DARK_GRAY, Color.WHITE, Color.GRAY, Color.GRAY, Color.CYAN);
            }

            public Locale locale() {
                return Locale.UK;
            }

            public String get(String key, String fallback) {
                return settings.getOrDefault(key, fallback);
            }

            public void set(String key, String value) {
                settings.put(key, value);
            }

            public void every(Duration period, Runnable task) {
            }

            public void onThemeChange(Runnable task) {
            }

            public void open(String... command) {
            }
        };
        DesktopWidget clock = new ClockWidget();
        BufferedImage img = new BufferedImage(220, 250, BufferedImage.TYPE_INT_ARGB);
        var face = clock.create(context);
        face.setSize(220, 250);
        face.paint(img.createGraphics());
        assertNotEquals(0, img.getRGB(110, 110) >>> 24, "the face is painted");
        settings.put("style", "digital");
        face.paint(img.createGraphics());
    }

    @Test
    void partsThatTakeTheMouseThemselvesPassItToTheCard() {
        org.junit.jupiter.api.Assumptions.assumeFalse(java.awt.GraphicsEnvironment.isHeadless());
        javax.swing.JLabel plain = new javax.swing.JLabel("plain");
        javax.swing.JLabel clickable = new javax.swing.JLabel("opens the program");
        clickable.addMouseListener(new java.awt.event.MouseAdapter() {
        });
        javax.swing.JTextField field = new javax.swing.JTextField();
        javax.swing.JLabel ownMenu = new javax.swing.JLabel("own menu");
        ownMenu.addMouseListener(new java.awt.event.MouseAdapter() {
        });
        ownMenu.setComponentPopupMenu(new javax.swing.JPopupMenu());
        javax.swing.JPanel content = new javax.swing.JPanel();
        for (var c : List.of(plain, clickable, field, ownMenu)) content.add(c);
        DesktopWidget widget = new DesktopWidget() {
            public String id() {
                return "test";
            }

            public String name() {
                return "Test";
            }

            public String description() {
                return "";
            }

            public java.awt.Dimension defaultSize() {
                return new java.awt.Dimension(200, 200);
            }

            public javax.swing.JComponent create(WidgetContext context) {
                return content;
            }
        };
        WidgetWindow window = new WidgetWindow("test-1", widget, new HostContext(dir.resolve("test-1.conf")),
                new Rectangle(0, 0, 200, 200), r -> {
                }, () -> {
                });
        try {
            assertTrue(window.forwards(clickable), "a label with its own listener keeps the events from the card");
            assertFalse(window.forwards(plain), "Swing sends the events of a plain label to the card anyway");
            assertFalse(window.forwards(field), "text fields keep their own mouse");
            assertFalse(window.forwards(ownMenu), "a part with its own menu keeps the right button");
            assertFalse(window.forwards(new javax.swing.JLabel("elsewhere")));
        } finally {
            window.close();
        }
    }

    /** Words that are the same in English and that language. */
    private static final java.util.Set<String> SAME = java.util.Set.of("de:gallery.title", "it:gallery.title",
            "de:photo.name", "fr:photo.name", "it:photo.name", "es:photo.name", "fr:photo.description");

    @Test
    void everyLanguageHasEveryText() {
        ResourceBundle en = ResourceBundle.getBundle(WidgetTexts.BUNDLE, Locale.ROOT);
        for (String lang : new String[]{"de", "fr", "es", "it"}) {
            ResourceBundle other = ResourceBundle.getBundle(WidgetTexts.BUNDLE, Locale.of(lang));
            for (String key : en.keySet()) {
                assertTrue(other.containsKey(key) && !other.getString(key).equals(en.getString(key))
                        || SAME.contains(lang + ":" + key),
                        lang + " " + key);
            }
        }
    }
}
