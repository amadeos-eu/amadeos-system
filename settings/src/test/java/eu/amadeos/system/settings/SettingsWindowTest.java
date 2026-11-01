package eu.amadeos.system.settings;

import eu.amadeos.system.clipboard.ClipboardSettings;
import eu.amadeos.system.clipboard.ClipboardSettingsStore;
import eu.amadeos.system.input.InputApplier;
import eu.amadeos.system.input.InputSettings;
import eu.amadeos.system.input.InputSettingsStore;
import eu.amadeos.system.input.XCommands;
import eu.amadeos.system.screen.ScreenSettings;
import eu.amadeos.system.screen.ScreenSettingsStore;
import eu.amadeos.system.base.window.WindowSettings;
import eu.amadeos.system.base.window.WindowSettingsStore;
import eu.amadeos.system.settings.SettingsStores.View;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class SettingsWindowTest {

    /** A Concave-like CDE pattern: the four shading symbols, one of them keyed by a backslash as in SkyDark.pm. */
    private static final String SHADED = """
            /* XPM */
            static char * shaded[] = {
            /* width height ncolors cpp [x_hot y_hot] */
            "4 2 4 1 0 0",
            /* colors */
            "\\    s bottomShadowColor m black c #636363636363",
            ".    s selectColor m white c #737373737373",
            "X    s background    m black c #949494949494",
            "o    s topShadowColor m white c #bdbdbdbdbdbd",
            /* pixels */
            "\\.Xo",
            "oX.\\"};
            """;
    @TempDir
    Path dir;

    @BeforeAll
    static void english() {
        // The tests compare English texts, whatever the desktop language of the machine.
        SettingsTexts.use(Locale.ENGLISH);
    }

    private SettingsWindow window() {
        return new SettingsWindow(SettingsStores.in(dir), new RecordingApplier(), SystemPagesTest.fakeSystem());
    }

    @Test
    void theSidebarShowsEveryControlAndOpensThePageOfTheChosenOne() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        SwingUtilities.invokeAndWait(() -> {
            SettingsWindow window = window();
            try {
                assertEquals("Settings", window.getTitle());
                assertEquals(View.SIDEBAR, window.view(), "the sidebar is the default view");
                for (SettingsCategory category : SettingsCategory.values()) {
                    assertEquals(category.available(), window.entry(category).isEnabled(), category.name());
                }
                assertEquals(SettingsCategory.COLOR, window.selected());
                assertInstanceOf(ColorPage.class, window.page(SettingsCategory.COLOR));

                window.openControl(SettingsCategory.WINDOW);
                assertEquals(SettingsCategory.WINDOW, window.selected());
                SettingsPage page = window.page(SettingsCategory.WINDOW);
                assertInstanceOf(WindowPage.class, page);

                window.reset(SettingsCategory.WINDOW);
                assertNotSame(page, window.page(SettingsCategory.WINDOW), "Reset builds the page anew");
                assertEquals("Window: changes undone", window.status());
            } finally {
                window.dispose();
            }
        });
    }

    @Test
    void searchFindsControlsByNameDescriptionAndKeyword() throws Exception {
        assertTrue(SettingsWindow.matches("LOCK").containsAll(List.of(SettingsCategory.SCREEN, SettingsCategory.PASSWORDS)));
        assertEquals(List.of(SettingsCategory.BACKDROP), SettingsWindow.matches("wallpaper"));
        assertTrue(SettingsWindow.matches("bell").contains(SettingsCategory.BEEP));
        assertTrue(SettingsWindow.matches("zzz").isEmpty());
        assertTrue(SettingsWindow.matches("login").contains(SettingsCategory.STARTUP));
        assertEquals(List.of(SettingsCategory.PASSWORDS), SettingsWindow.matches("keyring"));
        assertEquals(List.of(SettingsCategory.DATETIME), SettingsWindow.matches("time zone"));
        assumeFalse(GraphicsEnvironment.isHeadless());
        SwingUtilities.invokeAndWait(() -> {
            SettingsWindow window = window();
            try {
                window.searchField().setText("screen saver");
                assertTrue(window.entry(SettingsCategory.COLOR).dimmed);
                assertFalse(window.entry(SettingsCategory.SCREEN).dimmed);
                window.searchField().postActionEvent(); // Enter opens the first match
                assertEquals(SettingsCategory.SCREEN, window.selected());
                assertEquals("", window.searchField().getText());
                assertFalse(window.entry(SettingsCategory.COLOR).dimmed);
            } finally {
                window.dispose();
            }
        });
    }

    @Test
    void theCompactViewIsCdesRowOfControlsAndIsRemembered() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        SwingUtilities.invokeAndWait(() -> {
            SettingsWindow window = window();
            try {
                window.switchView(View.COMPACT);
                for (SettingsCategory category : SettingsCategory.values()) {
                    assertEquals(category.available(), window.button(category).isEnabled(), category.name());
                    assertEquals(category.label(), window.button(category).getText());
                }
            } finally {
                window.dispose();
            }
            SettingsWindow again = window();
            try {
                assertEquals(View.COMPACT, again.view());
            } finally {
                again.dispose();
            }
        });
    }

    @Test
    void screenDialogSavesTheSettings() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        ScreenSettingsStore store = new ScreenSettingsStore(dir.resolve("screen.conf"));
        AtomicReference<String> status = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            ScreenPage dialog = new ScreenPage(status::set, store);
            String message = dialog.apply();
            status.set(message);
            assertEquals(ScreenSettings.DEFAULTS, store.load());
        });
        assertTrue(status.get().startsWith("Screen: saver after 10 min"), status.get());
    }

    @Test
    void clipboardDialogSavesTheSettingsAndClearsTheHistory() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        ClipboardSettingsStore store = new ClipboardSettingsStore(dir.resolve("clipboard.conf"));
        AtomicReference<String> status = new AtomicReference<>();
        List<String> cleared = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            ClipboardPage dialog = new ClipboardPage(status::set, store, () -> cleared.add("clear"));
            assertEquals("Clipboard: history of 30 entries, copies kept after closing", dialog.apply());
            assertEquals(ClipboardSettings.DEFAULTS, store.load());
            findButton(dialog, "Clear History").doClick();
            assertEquals(List.of("clear"), cleared);
            assertEquals("Clipboard history cleared", status.get());
        });
    }

    private static javax.swing.AbstractButton findButton(java.awt.Container root, String text) {
        for (java.awt.Component c : root.getComponents()) {
            if (c instanceof javax.swing.AbstractButton b && text.equals(b.getText())) return b;
            if (c instanceof java.awt.Container child) {
                javax.swing.AbstractButton found = findButton(child, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Test
    void openingAControlTwiceReusesTheDialog() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        SwingUtilities.invokeAndWait(() -> {
            SettingsWindow manager = window();
            manager.switchView(View.COMPACT);
            try {
                manager.openControl(SettingsCategory.SCREEN);
                manager.openControl(SettingsCategory.SCREEN);
                long dialogs = java.util.Arrays.stream(Window.getWindows())
                        .filter(w -> w instanceof JDialog && w.isDisplayable() && w.getOwner() == manager)
                        .count();
                assertEquals(1L, dialogs);
                manager.openControl(SettingsCategory.STARTUP); // not available: no dialog, no exception
            } finally {
                for (Window w : manager.getOwnedWindows()) w.dispose();
                manager.dispose();
            }
        });
    }

    @Test
    void windowPageSavesWhatTheWindowManagerFollows() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        WindowSettingsStore store = new WindowSettingsStore(dir.resolve(WindowSettingsStore.FILE_NAME));
        store.save(new WindowSettings(WindowSettings.Focus.POINTER, false, true));
        SwingUtilities.invokeAndWait(() -> {
            WindowPage page = new WindowPage(s -> { }, store);
            assertEquals(new WindowSettings(WindowSettings.Focus.POINTER, false, true), page.selected(),
                    "the page shows the saved settings");
            findButton(page, "Click in window to make active").doClick();
            findButton(page, "Raise window when made active").doClick();
            assertEquals("Window applied", page.apply());
            assertEquals(new WindowSettings(WindowSettings.Focus.CLICK, true, true), store.load());
        });
    }

    @Test
    void keyboardMouseAndBeepSaveAndRunTheXTools() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        InputSettingsStore store = new InputSettingsStore(dir.resolve("input.conf"));
        RecordingApplier applier = new RecordingApplier();
        InputControls controls = new InputControls(store, applier);
        SwingUtilities.invokeAndWait(() -> {
            KeyboardPage keyboard = new KeyboardPage(s -> { }, controls);
            MousePage mouse = new MousePage(s -> { }, controls);
            BeepPage beep = new BeepPage(s -> { }, controls);
            assertEquals("Keyboard applied", keyboard.apply());
            assertEquals(InputSettings.DEFAULTS, store.load());
            assertTrue(applier.lines().contains("xset r rate 660 25"), applier.lines().toString());
            assertTrue(mouse.apply().startsWith("Mouse applied"));
            assertTrue(applier.lines().contains("xmodmap -e pointer = 1 2 3 4 5"), applier.lines().toString());
            beep.apply();
            assertTrue(applier.lines().contains("xset b 50 400 100"));
        });
    }

    @Test
    void beepCancelRestoresTheSavedBellAfterATest() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        InputSettingsStore store = new InputSettingsStore(dir.resolve("input.conf"));
        store.save(InputSettings.DEFAULTS.withBeep(new InputSettings.Beep(20, 800, 50)));
        RecordingApplier applier = new RecordingApplier();
        SwingUtilities.invokeAndWait(() -> {
            BeepPage beep = new BeepPage(s -> { }, new InputControls(store, applier));
            beep.cancelled(); // nothing tested yet: nothing to restore
            assertTrue(applier.lines().isEmpty());
        });
    }

    @Test
    void choosingACdePatternSwitchesTheBackdropToTile() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        java.nio.file.Files.writeString(dir.resolve("Pebbles.pm"), SHADED);
        String previous = System.getProperty(eu.amadeos.system.base.theme.backdrop.Backdrops.DIR_PROPERTY);
        System.setProperty(eu.amadeos.system.base.theme.backdrop.Backdrops.DIR_PROPERTY, dir.toString());
        try {
            pebblesSelectsTile();
        } finally {
            if (previous == null) System.clearProperty(eu.amadeos.system.base.theme.backdrop.Backdrops.DIR_PROPERTY);
            else System.setProperty(eu.amadeos.system.base.theme.backdrop.Backdrops.DIR_PROPERTY, previous);
        }
    }

    private void pebblesSelectsTile() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            BackdropPage dialog = new BackdropPage(s -> { });
            javax.swing.JList<?> list = find(dialog, javax.swing.JList.class);
            javax.swing.JComboBox<?> scale = find(dialog, javax.swing.JComboBox.class,
                    c -> c.getItemAt(0) instanceof eu.amadeos.system.base.theme.WallpaperManager.ScaleMode);
            javax.swing.JComboBox<?> workspace = find(dialog, javax.swing.JComboBox.class,
                    c -> c != scale);
            assertEquals(5, workspace.getItemCount(), "All workspaces, then the four");
            assertEquals(SettingsTexts.get("backdrop.workspace.all"), workspace.getItemAt(0).toString());
            scale.setSelectedItem(eu.amadeos.system.base.theme.WallpaperManager.ScaleMode.FILL);
            for (int i = 0; i < list.getModel().getSize(); i++) {
                if (list.getModel().getElementAt(i).toString().equals("Pebbles")) list.setSelectedIndex(i);
            }
            assertEquals(eu.amadeos.system.base.theme.WallpaperManager.ScaleMode.TILE, scale.getSelectedItem());
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T find(java.awt.Container root, Class<T> type, java.util.function.Predicate<T> test) {
        for (java.awt.Component c : root.getComponents()) {
            if (type.isInstance(c) && test.test((T) c)) return (T) c;
            if (c instanceof java.awt.Container inner) {
                T found = find(inner, type, test);
                if (found != null) return found;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T> T find(java.awt.Container root, Class<T> type) {
        for (java.awt.Component c : root.getComponents()) {
            if (type.isInstance(c)) return (T) c;
            if (c instanceof java.awt.Container inner) {
                T found = find(inner, type);
                if (found != null) return found;
            }
        }
        return null;
    }

    /** Records the X commands instead of running them. */
    static final class RecordingApplier extends InputApplier {
        final List<XCommands.Command> commands = new ArrayList<>();

        @Override
        public List<XCommands.Command> apply(List<XCommands.Command> list) {
            commands.addAll(list);
            return List.of();
        }

        @Override
        public int pointerButtons() {
            return 5;
        }

        List<String> lines() {
            return commands.stream().map(c -> String.join(" ", c.args())).toList();
        }
    }
}
