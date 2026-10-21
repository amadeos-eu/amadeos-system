package eu.amadeos.system.software.ui;

import eu.amadeos.system.packages.CatalogEntry;
import eu.amadeos.system.packages.Category;
import org.junit.jupiter.api.Test;

import java.util.List;

import static eu.amadeos.system.software.ui.UpdateTableModelTest.EN;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CatalogFilterTest {
    private static CatalogEntry entry(String name, String summary, String installed) {
        return new CatalogEntry(name, summary, "utils", Category.COMMAND_LINE, installed, "2.0", false);
    }

    private static final List<CatalogEntry> ENTRIES = List.of(
            entry("neovim", "heavily refactored vim fork", ""),
            entry("vim", "Vi IMproved - enhanced vi editor", "2.0"),
            entry("vim-tiny", "Vi IMproved - enhanced vi editor - compact version", ""),
            entry("zsh", "shell with lots of features", "1.0"));

    private static List<String> names(CatalogFilter filter) {
        return filter.apply(ENTRIES).stream().map(CatalogEntry::name).toList();
    }

    @Test
    void theNameItselfComesFirstThenNamesStartingWithTheWord() {
        assertEquals(List.of("vim", "vim-tiny", "neovim"), names(CatalogFilter.of("Vim", false)));
        assertEquals(List.of("vim", "vim-tiny"), names(CatalogFilter.of("vim editor", false)));
        assertEquals(List.of("zsh"), names(CatalogFilter.of(" shell  ", false)));
        assertEquals(List.of("neovim", "vim", "vim-tiny", "zsh"), names(CatalogFilter.of("", false)));
    }

    @Test
    void installedOnly() {
        assertEquals(List.of("vim", "zsh"), names(CatalogFilter.of("", true)));
        assertEquals(List.of("vim"), names(CatalogFilter.of("vim", true)));
    }

    @Test
    void statusAndDetailsTexts() {
        CatalogEntry upgradable = entry("zsh", "", "1.0");
        assertEquals("Update available", CatalogTableModel.status(EN, upgradable));
        assertEquals("Installed", CatalogTableModel.status(EN, entry("vim", "", "2.0")));
        assertEquals("", CatalogTableModel.status(EN, entry("neovim", "", "")));
        assertEquals("Not installed. Version 2.0 is available.",
                CatalogDetails.stateText(EN, entry("neovim", "", "")));
        assertEquals("Installed: version 1.0. No repository offers it any more. Installed because other software "
                + "needs it.", CatalogDetails.stateText(EN,
                new CatalogEntry("old", "", "utils", Category.OTHER, "1.0", "", true)));
        assertEquals("820 KB", Sizes.bytes(820_000));
        assertEquals("14.2 MB", Sizes.bytes(14_200_000).replace(',', '.'));
        assertEquals("512 B", Sizes.bytes(512));
    }
}
