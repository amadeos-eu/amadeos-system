package eu.amadeos.system.polkit;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import eu.amadeos.commons.i18n.TranslationCheck;
import eu.amadeos.system.polkit.dbus.Subject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PolkitAgentTest {
    @Test
    void theAgentServesTheLoginSessionOrOneProcess() {
        Subject session = PolkitAgent.subject(new String[0], Map.of("XDG_SESSION_ID", "3")).orElseThrow();
        assertEquals("unix-session", session.kind);
        assertEquals("3", session.details.get("session-id").getValue());
        String self = Long.toString(ProcessHandle.current().pid());
        Subject process = PolkitAgent.subject(new String[]{"--process", self}, Map.of()).orElseThrow();
        assertEquals("unix-process", process.kind);
        assertEquals(ProcessHandle.current().pid(), ((Number) process.details.get("pid").getValue()).longValue());
        assertEquals(Optional.empty(), PolkitAgent.subject(new String[]{"--process", "999999999"}, Map.of()));
        assertEquals(Optional.empty(), PolkitAgent.subject(new String[0], Map.of()));
        assertEquals(Optional.empty(), PolkitAgent.subject(new String[]{"--process", "x"}, Map.of()));
    }

    @Test
    void theStartTimeIsField22EvenWithSpacesInTheName() {
        assertEquals(Optional.of(98765L), PolkitAgent.startTime(
                "4711 (my (odd) prog) S 1 4711 4711 0 -1 4194560 100 0 0 0 1 2 0 0 20 0 1 0 98765 1000 50"));
    }

    @Test
    void polkitGetsAPosixLocale() {
        assertEquals("de_DE.UTF-8", PolkitAgent.polkitLocale(Locale.GERMANY));
        assertEquals("it.UTF-8", PolkitAgent.polkitLocale(Locale.ITALIAN));
    }

    @Test
    void identitiesTellTheirUserId() {
        assertEquals(1000, UsersTest.user(1000).uid());
        assertEquals(-1, Subject.session("1").uid());
    }

    @Test
    void everyTranslationIsComplete() {
        assertEquals(Map.of(), TranslationCheck.problems(PolkitAgent.class, PolkitAgent.TEXTS));
    }
}
