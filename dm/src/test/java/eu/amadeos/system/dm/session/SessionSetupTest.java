package eu.amadeos.system.dm.session;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import eu.amadeos.commons.i18n.Language;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SessionSetupTest {
    private final UserAccount alice = new UserAccount("alice", 1001, 1001, "/home/alice", "/bin/bash");

    @Test
    void sessionTypeComesFromGreeterLabelOrId() {
        assertEquals(SessionType.FAILSAFE, SessionType.from("Failsafe Terminal"));
        assertEquals(SessionType.FAILSAFE, SessionType.from("failsafe"));
        assertEquals(SessionType.DESKTOP, SessionType.from("amadeOS CDE Desktop"));
        assertEquals(SessionType.DESKTOP, SessionType.from("something else"));
    }

    @Test
    void environmentIsBuiltFromPamAndAccountOnly() {
        Map<String, String> env = SessionEnvironment.build(
                Map.of("XDG_RUNTIME_DIR", "/run/user/1001", "LANG", "de_DE.UTF-8", "HOME", "/wrong"),
                alice, ":0", "/home/alice/.Xauthority", SessionType.DESKTOP);
        assertEquals("/run/user/1001", env.get("XDG_RUNTIME_DIR"));
        assertEquals("de_DE.UTF-8", env.get("LANG"));
        assertEquals("/home/alice", env.get("HOME"), "account values win over PAM");
        assertEquals("alice", env.get("USER"));
        assertEquals("alice", env.get("LOGNAME"));
        assertEquals("/bin/bash", env.get("SHELL"));
        assertEquals(":0", env.get("DISPLAY"));
        assertEquals("/home/alice/.Xauthority", env.get("XAUTHORITY"));
        assertEquals(SessionEnvironment.DEFAULT_PATH, env.get("PATH"));
        assertEquals("desktop", env.get("AMADEOS_SESSION"));
        assertFalse(env.containsKey("JAVA_HOME"), "nothing from the display manager's environment");
    }

    @Test
    void theLoginLanguageReplacesTheSystemLocale() {
        Map<String, String> env = SessionEnvironment.build(
                Map.of("LANG", "de_DE.UTF-8", "LC_ALL", "de_DE.UTF-8", "LANGUAGE", "de_DE:de:en_US:en"),
                alice, ":0", null, SessionType.DESKTOP, Optional.of(Language.ITALIAN));
        assertEquals("it_IT.UTF-8", env.get("LANG"));
        assertEquals("it_IT.UTF-8", env.get(SessionEnvironment.LOGIN_LOCALE));
        assertFalse(env.containsKey("LC_ALL"), "LC_ALL would override LANG");
        assertFalse(env.containsKey("LANGUAGE"), "LANGUAGE would override the message language");

        Map<String, String> unchanged = SessionEnvironment.build(Map.of("LC_ALL", "de_DE.UTF-8"),
                alice, ":0", null, SessionType.DESKTOP, Optional.empty());
        assertEquals("de_DE.UTF-8", unchanged.get("LC_ALL"));
        assertFalse(unchanged.containsKey(SessionEnvironment.LOGIN_LOCALE));
    }

    @Test
    void pamPathIsKept() {
        Map<String, String> env = SessionEnvironment.build(Map.of("PATH", "/custom/bin"), alice, ":0", null,
                SessionType.DESKTOP);
        assertEquals("/custom/bin", env.get("PATH"));
        assertFalse(env.containsKey("XAUTHORITY"));
    }

    @Test
    void logindVariablesDescribeAnX11SeatSession() {
        Map<String, String> vars = SessionEnvironment.logindVariables(":0", 7);
        assertEquals("seat0", vars.get("XDG_SEAT"));
        assertEquals("7", vars.get("XDG_VTNR"));
        assertEquals("x11", vars.get("XDG_SESSION_TYPE"));
        assertEquals("user", vars.get("XDG_SESSION_CLASS"));
    }

    @Test
    void rootDropsPrivilegesWithSetpriv() {
        assertEquals(List.of("setpriv", "--reuid=1001", "--regid=1001", "--init-groups", "--",
                        "/usr/bin/amadeos-session", ":0"),
                SessionCommand.build(alice, SessionType.DESKTOP, "/usr/bin/amadeos-session", ":0", true));
    }

    @Test
    void failsafeStartsATerminalAndNonRootRunsDirectly() {
        List<String> failsafe = SessionCommand.build(alice, SessionType.FAILSAFE, "/x", ":0", true);
        assertEquals("setpriv", failsafe.get(0));
        assertTrue(failsafe.contains("xterm"));
        assertEquals(List.of("/x", ":1"), SessionCommand.build(alice, SessionType.DESKTOP, "/x", ":1", false));
    }

    @Test
    void vtFallsBackToSeven() {
        assertEquals(7, SessionWorker.parseVt(null));
        assertEquals(7, SessionWorker.parseVt("abc"));
        assertEquals(2, SessionWorker.parseVt(" 2 "));
    }
}
