package eu.amadeos.system.settings;

import eu.amadeos.system.display.DisplayMode;
import eu.amadeos.system.display.DisplaySettingsStore;
import eu.amadeos.system.display.OutputSetting;
import eu.amadeos.system.display.Rotation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** The Display page against an xrandr that knows a laptop and a monitor ({@link FakeDisplays}). */
class DisplayPageTest {
    @TempDir
    Path dir;

    @BeforeAll
    static void english() {
        SettingsTexts.use(Locale.ENGLISH);
    }

    @Test
    void keptLayoutIsSetAndSaved() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        FakeDisplays displays = new FakeDisplays();
        DisplaySettingsStore store = new DisplaySettingsStore(dir.resolve(DisplaySettingsStore.FILE_NAME));
        SwingUtilities.invokeAndWait(() -> {
            DisplayPage page = new DisplayPage(s -> { }, displays, store);
            page.setKeepQuestion(answer -> answer.accept(true));
            assertEquals("eDP-1", page.selected(), "the primary monitor comes first");
            page.select("HDMI-1");
            page.rotation().setSelectedItem(Rotation.LEFT);
            assertNull(page.apply(), "the status follows the answer");
        });
        assertEquals(List.of("xrandr", "--output", "eDP-1", "--mode", "1920x1080", "--rate", "60.02", "--pos", "0x0",
                "--rotate", "normal", "--primary", "--output", "HDMI-1", "--mode", "1920x1080", "--rate", "60.00",
                "--pos", "1920x0", "--rotate", "left"), displays.commands.get(1));
        OutputSetting monitor = store.load(List.of("eDP-1", "HDMI-1")).orElseThrow().getFirst();
        assertEquals(Rotation.LEFT, monitor.rotation());
    }

    @Test
    void unansweredLayoutGoesBack() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        FakeDisplays displays = new FakeDisplays();
        DisplaySettingsStore store = new DisplaySettingsStore(dir.resolve(DisplaySettingsStore.FILE_NAME));
        SwingUtilities.invokeAndWait(() -> {
            DisplayPage page = new DisplayPage(s -> { }, displays, store);
            page.setKeepQuestion(answer -> answer.accept(false));
            DisplayMode small = page.resolution().getItemAt(page.resolution().getItemCount() - 1);
            page.resolution().setSelectedItem(small);
            page.apply();
            assertEquals(1920, page.settings().getFirst().width(), "the page shows the old layout again");
        });
        List<String> revert = displays.commands.get(displays.commands.size() - 2);
        assertTrue(revert.containsAll(List.of("--mode", "1920x1080", "--rate", "60.02")), revert.toString());
        assertEquals(Optional.empty(), store.load(List.of("eDP-1", "HDMI-1")), "nothing is saved");
    }

    @Test
    void lastMonitorStaysOnAndMirrorSharesAResolution() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        FakeDisplays displays = new FakeDisplays();
        SwingUtilities.invokeAndWait(() -> {
            DisplayPage page = new DisplayPage(s -> { }, displays,
                    new DisplaySettingsStore(dir.resolve(DisplaySettingsStore.FILE_NAME)));
            page.use().doClick();
            assertFalse(page.settings().getFirst().enabled());
            assertEquals(0, page.settings().get(1).x(), "the other monitor moves to the left edge");
            page.select("HDMI-1");
            page.use().doClick();
            assertTrue(page.settings().get(1).enabled(), "the last monitor that is on stays on");

            page.select("eDP-1");
            page.use().doClick();
            page.mirror().doClick();
            assertTrue(page.settings().stream().allMatch(s -> s.x() == 0 && s.y() == 0 && s.width() == 1920));
        });
    }

    @Test
    void refusedLayoutIsAnError() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        FakeDisplays displays = new FakeDisplays();
        SwingUtilities.invokeAndWait(() -> {
            DisplayPage page = new DisplayPage(s -> { }, displays,
                    new DisplaySettingsStore(dir.resolve(DisplaySettingsStore.FILE_NAME)));
            page.rotation().setSelectedItem(Rotation.INVERTED);
            displays.refuse = true;
            assertThrows(IllegalStateException.class, page::apply);
        });
    }
}
