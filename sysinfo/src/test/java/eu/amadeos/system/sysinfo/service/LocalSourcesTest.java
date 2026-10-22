package eu.amadeos.system.sysinfo.service;

import eu.amadeos.commons.process.CommandRunner;
import eu.amadeos.commons.process.FinishedProcess;
import eu.amadeos.commons.process.ProgramMissingException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocalSourcesTest {
    // --8<-- [start:answers]
    @Test
    void aCommandThatIsMissingOrFailsGivesNoAnswer() {
        CommandRunner runner = command -> switch (command.getFirst()) {
            case "lspci" -> FinishedProcess.printing("00:02.0 \"VGA compatible controller\" \"Red Hat, Inc.\" \"Virtio GPU\"\n");
            case "broken" -> FinishedProcess.failing(1, "no such device");
            default -> throw new ProgramMissingException(command.getFirst(), new IOException("error=2"));
        };
        LocalSources sources = new LocalSources(runner);

        assertEquals(Optional.of("00:02.0 \"VGA compatible controller\" \"Red Hat, Inc.\" \"Virtio GPU\"\n"),
                sources.run(List.of("lspci", "-mm")));
        assertEquals(Optional.empty(), sources.run(List.of("broken")));
        assertEquals(Optional.empty(), sources.run(List.of("not-installed")));
    }
    // --8<-- [end:answers]
}
