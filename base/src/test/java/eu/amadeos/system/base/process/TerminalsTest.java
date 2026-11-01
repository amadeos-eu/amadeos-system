package eu.amadeos.system.base.process;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TerminalsTest {
    @Test
    void environmentWins() {
        assertEquals("kitty", Terminals.program(" kitty ", p -> true));
    }

    @Test
    void amadeTerminalBeforeTheAlternativeAndXterm() {
        assertEquals("amade-terminal", Terminals.program(null, p -> true));
        assertEquals("x-terminal-emulator", Terminals.program("", Set.of("x-terminal-emulator", "xterm")::contains));
        assertEquals("xterm", Terminals.program(null, Set.of("xterm")::contains));
        assertEquals("xterm", Terminals.program(null, p -> false));
    }

    @Test
    void commandWithTitle() {
        assertEquals(List.of("amade-terminal", "-title", "Root Shell", "-e", "sudo", "sh"),
                Terminals.command("amade-terminal", "Root Shell", List.of("sudo", "sh")));
        assertEquals(List.of("xterm"), Terminals.command("xterm", null, List.of()));
    }
}
