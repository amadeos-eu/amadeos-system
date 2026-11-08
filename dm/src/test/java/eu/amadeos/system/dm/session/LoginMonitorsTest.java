package eu.amadeos.system.dm.session;

import java.awt.Rectangle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import eu.amadeos.system.display.DisplayApplier;
import eu.amadeos.system.display.Xrandr;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginMonitorsTest {
    /** A laptop with its own screen on and a monitor connected but dark: how Xorg starts. */
    private static final String AT_START = """
            Screen 0: minimum 320 x 200, current 1920 x 1080, maximum 16384 x 16384
            eDP-1 connected primary 1920x1080+0+0 (normal left inverted right x axis y axis) 309mm x 174mm
               1920x1080     60.02*+
            HDMI-1 connected (normal left inverted right x axis y axis) 527mm x 296mm
               2560x1440     59.95 +
               1920x1080     60.00
            """;

    /** The layout Ralf kept: laptop screen off, the monitor alone and primary. */
    private static final String MONITOR_ONLY = """
            Screen 0: minimum 320 x 200, current 2560 x 1440, maximum 16384 x 16384
            eDP-1 connected (normal left inverted right x axis y axis) 309mm x 174mm
               1920x1080     60.02 +
            HDMI-1 connected primary 2560x1440+0+0 (normal left inverted right x axis y axis) 527mm x 296mm
               2560x1440     59.95*+
               1920x1080     60.00
            """;

    @TempDir
    Path home;

    /** Answers --query with the first text until a layout was set, then with the second. */
    private static final class FakeXrandr extends DisplayApplier {
        final List<List<String>> calls = new ArrayList<>();
        String screen = AT_START;

        @Override
        protected int run(List<String> command, StringBuilder output) {
            calls.add(command);
            if (command.contains("--query")) {
                output.append(screen);
            } else {
                screen = MONITOR_ONLY;
            }
            return 0;
        }
    }

    @Test
    void setsTheLayoutTheUserKeptAndGreetsOnThePrimaryMonitor() throws Exception {
        Path file = LoginMonitors.layoutFile(home);
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                HDMI-1+eDP-1.eDP-1=off
                HDMI-1+eDP-1.HDMI-1=2560x1440 59.95 0 0 normal primary
                """);
        FakeXrandr xrandr = new FakeXrandr();
        LoginMonitors monitors = new LoginMonitors(xrandr);
        assertEquals(Optional.of(new Rectangle(0, 0, 1920, 1080)), monitors.loginArea());

        monitors.applyLayoutIn(home);

        assertEquals(3, xrandr.calls.size(), "query, query, set");
        List<String> set = xrandr.calls.get(2);
        assertEquals("xrandr", set.get(0));
        assertTrue(set.containsAll(List.of("--output", "eDP-1", "--off")), set.toString());
        assertTrue(set.containsAll(List.of("--output", "HDMI-1", "--mode", "2560x1440", "--primary")), set.toString());
        assertEquals(Optional.of(new Rectangle(0, 0, 2560, 1440)), monitors.loginArea());
    }

    @Test
    void leavesTheMonitorsAloneWithoutAKeptLayout() throws Exception {
        FakeXrandr xrandr = new FakeXrandr();
        new LoginMonitors(xrandr).applyLayoutIn(home);
        assertTrue(xrandr.calls.isEmpty(), "no display.conf, no xrandr");

        Path other = home.resolve("other");
        Files.createDirectories(other);
        Path file = LoginMonitors.layoutFile(home);
        Files.createDirectories(file.getParent());
        Files.writeString(other.resolve("display.conf"), "HDMI-1+eDP-1.eDP-1=off\n");
        Files.createSymbolicLink(file, other.resolve("display.conf"));
        new LoginMonitors(xrandr).applyLayoutIn(home);
        assertTrue(xrandr.calls.isEmpty(), "a link is not followed as root");

        Files.delete(file);
        Files.writeString(file, "DP-3+HDMI-1.HDMI-1=1920x1080 60.00 0 0 normal primary\n");
        new LoginMonitors(xrandr).applyLayoutIn(home);
        assertEquals(1, xrandr.calls.size(), "only the query: nothing kept for these monitors");
    }

    @Test
    void theLoginAreaIsThePrimaryElseTheLeftmostMonitor() {
        String twoOn = """
                Screen 0: minimum 320 x 200, current 3000 x 1920, maximum 16384 x 16384
                eDP-1 connected 1920x1080+1080+0 (normal left inverted right x axis y axis) 309mm x 174mm
                   1920x1080     60.02*+
                HDMI-1 connected 1080x1920+0+0 left (normal left inverted right x axis y axis) 527mm x 296mm
                   1920x1080     60.00*+
                """;
        assertEquals(Optional.of(new Rectangle(0, 0, 1080, 1920)), LoginMonitors.loginArea(Xrandr.parse(twoOn)),
                "no primary: the leftmost, turned on its side");
        assertEquals(Optional.of(new Rectangle(1080, 0, 1920, 1080)),
                LoginMonitors.loginArea(Xrandr.parse(twoOn.replace("eDP-1 connected", "eDP-1 connected primary"))));
        assertEquals(Optional.empty(), LoginMonitors.loginArea(List.of()));
    }
}
