package org.datazuul.amadeos.input;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class XCommandsTest {
    @TempDir
    Path dir;

    private static List<String> lines(List<XCommands.Command> commands) {
        return commands.stream().map(c -> String.join(" ", c.args())).toList();
    }

    @Test
    void keyboardCommands() {
        assertEquals(List.of("xset r on", "xset r rate 660 25", "xset c off"),
                lines(XCommands.keyboard(InputSettings.Keyboard.DEFAULTS)));
        assertEquals(List.of("xset r off", "xset c 40"),
                lines(XCommands.keyboard(new InputSettings.Keyboard(false, 500, 30, 40))));
    }

    @Test
    void mouseCommandsSwapOnlyTheFirstAndThirdButton() {
        List<XCommands.Command> commands = XCommands.mouse(new InputSettings.Mouse(true, 300, 3, 6), 9);
        assertEquals(List.of("xset m 3/1 6", "xmodmap -e pointer = 3 2 1 4 5 6 7 8 9", "xrdb -merge"), lines(commands));
        assertEquals("*multiClickTime: 300\n", commands.get(2).stdin());
        assertEquals("1 2 3", XCommands.buttonMap(false, 2), "at least three buttons");
    }

    @Test
    void beepCommand() {
        assertEquals(List.of("xset b 50 400 100"), lines(XCommands.beep(InputSettings.Beep.DEFAULTS)));
    }

    @Test
    void pointerButtonsFromXmodmap() {
        assertEquals(7, XCommands.pointerButtons("There are 7 pointer buttons defined.\n\n    Physical ..."));
        assertEquals(3, XCommands.pointerButtons("garbage"));
        assertEquals(3, XCommands.pointerButtons(null));
    }

    @Test
    void valuesAreClamped() {
        InputSettings.Mouse m = new InputSettings.Mouse(false, 5, 99, 0);
        assertEquals(100, m.doubleClickMs());
        assertEquals(10, m.acceleration());
        assertEquals(1, m.threshold());
        assertEquals(100, new InputSettings.Beep(500, 1, 1).volume());
    }

    @Test
    void storeRoundTripAndBrokenValues() throws Exception {
        InputSettingsStore store = new InputSettingsStore(dir.resolve("input.conf"));
        assertEquals(InputSettings.DEFAULTS, store.load());
        InputSettings custom = new InputSettings(new InputSettings.Keyboard(false, 300, 40, 10),
                new InputSettings.Mouse(true, 500, 4, 8), new InputSettings.Beep(0, 800, 200));
        store.save(custom);
        assertEquals(custom, store.load());

        Files.writeString(dir.resolve("input.conf"), "keyboard.repeatRate=fast\nbeep.volume=70\n");
        InputSettings loaded = store.load();
        assertEquals(InputSettings.Keyboard.DEFAULTS.repeatRate(), loaded.keyboard().repeatRate());
        assertEquals(70, loaded.beep().volume());
    }
}
