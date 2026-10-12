package org.datazuul.amadeos.dm.session;

import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SessionWorkerClientTest {
    @Test
    void withoutLoginUidTheJvmIsStartedDirectly() {
        List<String> command = SessionWorkerClient.workerCommand(":0", OptionalInt.empty());
        assertTrue(command.get(0).endsWith("/bin/java"));
        assertEquals(List.of("session-worker", ":0"), command.subList(command.size() - 2, command.size()));
    }

    @Test
    void loginUidIsSetByAShellThatExecsTheJvm() {
        List<String> direct = SessionWorkerClient.workerCommand(":0", OptionalInt.empty());
        List<String> command = SessionWorkerClient.workerCommand(":0", OptionalInt.of(1000));
        assertEquals("/bin/sh", command.get(0));
        assertEquals("-c", command.get(1));
        assertTrue(command.get(2).contains("/proc/self/loginuid"));
        assertEquals("1000", command.get(4));
        assertEquals(direct, command.subList(5, command.size()));
    }
}
