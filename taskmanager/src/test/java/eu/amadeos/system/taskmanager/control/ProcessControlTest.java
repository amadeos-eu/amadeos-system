package eu.amadeos.system.taskmanager.control;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProcessControlTest {
    private final List<List<String>> commands = new ArrayList<>();
    private int exitCode;
    private final ProcessControl control = new ProcessControl(c -> {
        commands.add(c);
        return exitCode;
    }, pid -> pid < 5000);

    @Test
    void ownProcessesDirectlyOthersThroughPkexec() throws IOException {
        control.terminate(1234);
        control.kill(6000);
        control.pause(1234);
        control.resume(1234);
        assertEquals(List.of(
                List.of("kill", "-s", "TERM", "1234"),
                List.of("pkexec", "kill", "-s", "KILL", "6000"),
                List.of("kill", "-s", "STOP", "1234"),
                List.of("kill", "-s", "CONT", "1234")), commands);
    }

    @Test
    void aHigherPriorityNeedsTheAdministrator() throws IOException {
        control.setPriority(1234, 0, ProcessControl.Priority.LOW);
        control.setPriority(1234, 5, ProcessControl.Priority.HIGH);
        assertEquals(List.of(
                List.of("renice", "-n", "5", "-p", "1234"),
                List.of("pkexec", "renice", "-n", "-5", "-p", "1234")), commands);
        assertEquals(ProcessControl.Priority.LOW, ProcessControl.Priority.of(4));
        assertEquals(ProcessControl.Priority.VERY_HIGH, ProcessControl.Priority.of(-20));
    }

    @Test
    void failuresAndCancelledPasswords() {
        assertThrows(IOException.class, () -> control.terminate(1), "init is never ended");
        exitCode = 1;
        assertThrows(IOException.class, () -> control.terminate(1234));
        exitCode = 126;
        assertThrows(ProcessControl.Cancelled.class, () -> control.terminate(6000));
    }
}
