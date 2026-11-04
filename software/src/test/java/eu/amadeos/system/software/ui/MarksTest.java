package eu.amadeos.system.software.ui;

import eu.amadeos.system.packages.CatalogEntry;
import eu.amadeos.system.packages.Category;
import eu.amadeos.system.packages.ChangeSet;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarksTest {

    private static CatalogEntry entry(String name, String installed, String available) {
        return new CatalogEntry(name, "", "misc", Category.APPLICATION, installed, available, false);
    }

    @Test
    void tickingMeansInstallUpdateOrRemoveAsTheStateSays() {
        assertEquals(Optional.of(Marks.Mark.INSTALL), Marks.defaultMark(entry("gimp", "", "2.10")));
        assertEquals(Optional.of(Marks.Mark.UPDATE), Marks.defaultMark(entry("vim", "9.0", "9.1")));
        assertEquals(Optional.of(Marks.Mark.REMOVE), Marks.defaultMark(entry("nano", "8.4", "8.4")));
        assertEquals(Optional.empty(), Marks.defaultMark(entry("gone", "", "")));
        assertEquals(Set.of(Marks.Mark.UPDATE, Marks.Mark.REMOVE), Marks.allowed(entry("vim", "9.0", "9.1")));
    }

    @Test
    void updatesAreMarkedOnceAndAnUnmarkedOneStaysUnmarked() {
        Marks marks = new Marks();
        int[] notices = {0};
        marks.addListener(() -> notices[0]++);
        marks.updatesRead(List.of("bash", "zsh"));
        assertEquals(List.of("bash", "zsh"), marks.names(Marks.Mark.UPDATE));
        marks.unmark("zsh");
        marks.updatesRead(List.of("bash", "zsh", "vim"));
        assertEquals(List.of("bash", "vim"), marks.names(Marks.Mark.UPDATE));
        // bash was updated elsewhere: its mark goes; a catalog mark of another package stays.
        marks.mark("gimp", Marks.Mark.INSTALL);
        marks.updatesRead(List.of("vim"));
        assertEquals(new ChangeSet(List.of("gimp"), List.of("vim"), List.of()), marks.changeSet());
        assertEquals(5, notices[0]);
    }

    @Test
    void marksThatNoLongerFitGoWhenTheCatalogIsReadAgain() {
        Marks marks = new Marks();
        marks.mark("gimp", Marks.Mark.INSTALL);
        marks.mark("nano", Marks.Mark.REMOVE);
        marks.mark("vim", Marks.Mark.UPDATE);
        marks.catalogRead(List.of(entry("gimp", "2.10", "2.10"), entry("nano", "8.4", "8.4"),
                entry("vim", "9.1", "9.1")));
        assertEquals(List.of("nano"), marks.names(Marks.Mark.REMOVE));
        assertEquals(1, marks.size());
        marks.forget(List.of("nano"));
        assertTrue(marks.isEmpty());
    }
}
