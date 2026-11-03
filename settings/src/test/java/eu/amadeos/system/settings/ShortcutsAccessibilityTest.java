package eu.amadeos.system.settings;

import eu.amadeos.system.base.shortcuts.KeyCombo;
import eu.amadeos.system.base.shortcuts.ShortcutAction;
import eu.amadeos.system.base.shortcuts.ShortcutStore;
import eu.amadeos.system.base.shortcuts.Shortcuts;
import eu.amadeos.system.input.AccessibilitySettings;
import eu.amadeos.system.input.AccessibilityStore;
import eu.amadeos.system.input.InputApplier;
import eu.amadeos.system.input.InputSettingsStore;
import eu.amadeos.system.input.XCommands;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShortcutsAccessibilityTest {
    @TempDir
    Path dir;

    @Test
    void keyPressesBecomeResults() {
        assertNull(KeyCapture.result("Control_L", 0));
        assertEquals(new KeyCapture.Result.Cancelled(), KeyCapture.result("Escape", 0));
        assertEquals(new KeyCapture.Result.Off(), KeyCapture.result("BackSpace", 16));
        assertEquals(new KeyCapture.Result.Keys(KeyCombo.of(KeyCombo.SUPER, "Escape")),
                KeyCapture.result("Escape", KeyCombo.SUPER | 16));
        assertEquals("F11", KeyCaptureDialog.keysym(KeyEvent.VK_F11));
        assertEquals("t", KeyCaptureDialog.keysym(KeyEvent.VK_T));
    }

    @Test
    void keysAreShownInTheLanguage() {
        try {
            SettingsTexts.use(java.util.Locale.GERMAN);
            assertEquals("Strg+Umschalt+Esc", ShortcutsPage.display(ShortcutAction.TASK_MANAGER.defaultKeys()));
            assertEquals("Super+Leertaste", ShortcutsPage.display(ShortcutAction.SEARCH.defaultKeys()));
        } finally {
            SettingsTexts.use(java.util.Locale.ENGLISH);
        }
        assertEquals("Ctrl+Alt+T", ShortcutsPage.display(ShortcutAction.TERMINAL.defaultKeys()));
    }

    @Test
    void applyWritesTheShortcutsFile() {
        ShortcutStore store = new ShortcutStore(dir.resolve(ShortcutStore.FILE_NAME));
        store.save(Shortcuts.DEFAULTS.with(ShortcutAction.SEARCH, Optional.empty()));
        AtomicReference<String> status = new AtomicReference<>();
        ShortcutsPage page = new ShortcutsPage(status::set, store, (held, done) -> () -> { });
        assertEquals(Optional.empty(), page.shortcuts().keys(ShortcutAction.SEARCH));
        assertTrue(page.applyChanges());
        assertEquals(page.shortcuts(), store.load());
        assertEquals("Keyboard Shortcuts applied", status.get());
    }

    @Test
    void highContrastRemembersThePaletteBefore() {
        List<List<String>> run = new ArrayList<>();
        InputApplier applier = new InputApplier() {
            @Override
            public List<XCommands.Command> apply(List<XCommands.Command> commands) {
                commands.forEach(c -> run.add(c.args()));
                return List.of();
            }
        };
        InputControls controls = new InputControls(new InputSettingsStore(dir.resolve("input.conf")), applier);
        AccessibilityStore store = new AccessibilityStore(dir.resolve(AccessibilityStore.FILE_NAME));
        AtomicReference<String> theme = new AtomicReference<>("cde-desert");
        AccessibilityPage.Themes themes = new AccessibilityPage.Themes(theme::get, theme::set);

        AccessibilityPage page = new AccessibilityPage(s -> { }, store, controls, () -> { }, themes);
        assertEquals(AccessibilitySettings.Contrast.OFF, page.settings().contrast());
        page.showContrast(AccessibilitySettings.Contrast.DARK);
        assertTrue(page.applyChanges());
        assertEquals("high-contrast-dark", theme.get());
        assertEquals("cde-desert", store.load().previousTheme());

        page = new AccessibilityPage(s -> { }, store, controls, () -> { }, themes);
        assertEquals(AccessibilitySettings.Contrast.DARK, page.settings().contrast());
        page.showContrast(AccessibilitySettings.Contrast.LIGHT);
        assertTrue(page.applyChanges());
        assertEquals("high-contrast-light", theme.get());
        assertEquals("cde-desert", store.load().previousTheme(), "not the other high contrast palette");

        page = new AccessibilityPage(s -> { }, store, controls, () -> { }, themes);
        page.showContrast(AccessibilitySettings.Contrast.OFF);
        assertTrue(page.applyChanges());
        assertEquals("cde-desert", theme.get());
        assertEquals(List.of("xsetroot", "-cursor_name", "left_ptr"), run.getLast());
    }

    @Test
    void contrastFollowsThePalette() {
        assertEquals(AccessibilitySettings.Contrast.DARK, AccessibilityPage.contrastOf("high-contrast-dark"));
        assertEquals(AccessibilitySettings.Contrast.OFF, AccessibilityPage.contrastOf("warm-motif"));
    }
}
