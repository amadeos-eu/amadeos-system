package eu.amadeos.system.display;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class XrandrTest {
    private static final String LAPTOP_AND_MONITOR = """
            Screen 0: minimum 320 x 200, current 3000 x 1920, maximum 16384 x 16384
            eDP-1 connected primary 1920x1080+0+0 (normal left inverted right x axis y axis) 309mm x 174mm
               1920x1080     60.02*+  59.93    48.00
               1680x1050     59.95    59.88
               1280x720      60.00
            HDMI-1 connected 1080x1920+1920+0 left (normal left inverted right x axis y axis) 527mm x 296mm
               1920x1080     60.00 +  74.97*   50.00    59.94
               1920x1080i    60.00    50.00
               1280x1024     75.02    60.02
            DP-1 disconnected (normal left inverted right x axis y axis)
            DP-2 connected (normal left inverted right x axis y axis)
               1024x768      60.00 +
            """;

    @Test
    void readsTheMonitorsTheirModesAndWhatIsOn() {
        List<Output> outputs = Xrandr.parse(LAPTOP_AND_MONITOR);
        assertEquals(List.of("eDP-1", "HDMI-1", "DP-1", "DP-2"), outputs.stream().map(Output::name).toList());

        Output laptop = outputs.get(0);
        assertTrue(laptop.connected() && laptop.primary() && laptop.enabled() && laptop.builtIn());
        assertEquals(new OutputSetting("eDP-1", true, 1920, 1080, 60.02, 0, 0, Rotation.NORMAL, true), laptop.current());
        assertEquals(List.of(60.02, 59.93, 48.00), laptop.modes().getFirst().rates());
        assertEquals(60.02, laptop.preferredMode().orElseThrow().preferredRate());
        assertEquals(14.0, laptop.diagonalInches());

        Output monitor = outputs.get(1);
        assertEquals(Rotation.LEFT, monitor.rotation());
        assertEquals(1920, monitor.width(), "the mode is read before rotation");
        assertEquals(1080, monitor.height());
        assertEquals(74.97, monitor.rate());
        assertEquals(1920, monitor.x());
        assertEquals(60.00, monitor.preferredMode().orElseThrow().preferredRate(), "the + stands apart from its rate");
        assertEquals(2, monitor.modes().size(), "interlaced modes are left out");
        assertEquals(1080, monitor.current().screenWidth());
        assertEquals(24.0, monitor.diagonalInches());

        assertFalse(outputs.get(2).connected());
        Output off = outputs.get(3);
        assertTrue(off.connected());
        assertFalse(off.enabled());
        assertEquals(0, off.width());
    }

    @Test
    void readsXvfb() {
        Output screen = Xrandr.parse("""
                Screen 0: minimum 1 x 1, current 1920 x 1080, maximum 1920 x 1080
                screen connected 1920x1080+0+0 0mm x 0mm
                   1920x1080      0.00*
                """).getFirst();
        assertTrue(screen.enabled());
        assertEquals(1920, screen.width());
        assertEquals(0, screen.diagonalInches());
        assertEquals("16:9", screen.modes().getFirst().aspect());
    }

    @Test
    void setsAllMonitorsInOneCallStartingAtZero() {
        List<String> command = Xrandr.command(List.of(
                new OutputSetting("eDP-1", true, 1920, 1080, 60.02, 100, 50, Rotation.NORMAL, true),
                new OutputSetting("HDMI-1", true, 1920, 1080, 0, 2020, 50, Rotation.RIGHT, false),
                new OutputSetting("DP-2", false, 0, 0, 0, 0, 0, Rotation.NORMAL, false)));
        assertEquals(List.of("xrandr",
                "--output", "eDP-1", "--mode", "1920x1080", "--rate", "60.02", "--pos", "0x0", "--rotate", "normal", "--primary",
                "--output", "HDMI-1", "--mode", "1920x1080", "--pos", "1920x0", "--rotate", "right",
                "--output", "DP-2", "--off"), command);
    }

    @Test
    void sideBySideKeepsTheOrderAndClosesGaps() {
        List<OutputSetting> placed = Xrandr.sideBySide(List.of(
                new OutputSetting("A", true, 1920, 1080, 60, 3000, 0, Rotation.LEFT, false),
                new OutputSetting("B", true, 1280, 1024, 60, 0, 200, Rotation.NORMAL, false)));
        assertEquals(1280, placed.get(0).x(), "the portrait monitor A stays to the right of B");
        assertEquals(0, placed.get(1).x());
        assertEquals(0, placed.get(1).y());
    }

    @Test
    void keepsOneLayoutPerSetOfMonitors(@TempDir Path dir) {
        DisplaySettingsStore store = new DisplaySettingsStore(dir.resolve(DisplaySettingsStore.FILE_NAME));
        List<OutputSetting> desk = List.of(
                new OutputSetting("HDMI-1", true, 2560, 1440, 59.95, 1920, 0, Rotation.NORMAL, true),
                new OutputSetting("eDP-1", true, 1920, 1080, 60.02, 0, 0, Rotation.NORMAL, false));
        List<OutputSetting> road = List.of(
                new OutputSetting("eDP-1", true, 1280, 720, 60.00, 0, 0, Rotation.INVERTED, true));
        store.save(desk);
        store.save(road);
        assertEquals(Optional.of(road), store.load(List.of("eDP-1")));
        assertEquals(Optional.of(List.of(desk.get(0), desk.get(1))), store.load(List.of("eDP-1", "HDMI-1")));
        assertEquals(Optional.empty(), store.load(List.of("eDP-1", "DP-2")), "no layout kept for this set");
        assertEquals("off", DisplaySettingsStore.format(new OutputSetting("DP-2", false, 0, 0, 0, 0, 0, Rotation.NORMAL, false)));
    }
}
