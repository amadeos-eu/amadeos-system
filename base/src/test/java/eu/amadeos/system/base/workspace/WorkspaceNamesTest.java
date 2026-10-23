package eu.amadeos.system.base.workspace;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceNamesTest {
    @TempDir
    Path config;

    @Test
    void aWorkspaceWithoutANameOfItsOwnKeepsTheDefault() {
        WorkspaceNames names = new WorkspaceNames(config);
        assertEquals(Optional.empty(), names.custom(0));
        assertEquals("Eins", names.name(0, "Eins"));
    }

    @Test
    void aNewNameIsStoredAndAnEmptyOneGivesBackTheDefault() throws Exception {
        WorkspaceNames names = new WorkspaceNames(config);
        assertEquals(Optional.of("Mail"), names.rename(1, "  Mail "));
        assertEquals("Mail", new WorkspaceNames(config).name(1, "Two"));
        assertTrue(Files.readString(config.resolve(WorkspaceNames.FILE)).contains("workspace.2.name=Mail"));

        assertEquals(Optional.empty(), names.rename(1, "   "));
        assertEquals("Two", names.name(1, "Two"));
    }

    @Test
    void namesAreOneShortLine() {
        assertEquals("Web Mail", WorkspaceNames.clean("Web\nMail"));
        assertEquals(WorkspaceNames.MAX_LENGTH, WorkspaceNames.clean("x".repeat(40)).length());
        assertEquals("", WorkspaceNames.clean(null));
    }

    @Test
    void thereAreFourWorkspaces() {
        WorkspaceNames names = new WorkspaceNames(config);
        assertThrows(IllegalArgumentException.class, () -> names.custom(4));
        assertThrows(IllegalArgumentException.class, () -> names.rename(-1, "x"));
    }
}
