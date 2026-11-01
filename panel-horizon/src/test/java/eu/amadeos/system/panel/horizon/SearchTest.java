package eu.amadeos.system.panel.horizon;

import eu.amadeos.system.base.xdg.XdgDesktopEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SearchTest {
    @TempDir
    Path home;

    private static XdgDesktopEntry entry(String id, String name, String generic, String comment) {
        return new XdgDesktopEntry(id, name, generic, comment, "icon", "/usr/bin/" + id, false, List.of("Utility"),
                List.of(), false, false, List.of(), List.of(), "", Path.of("/usr/share/applications/" + id));
    }

    private final List<XdgDesktopEntry> entries = List.of(
            entry("amade-writepad.desktop", "WritePad", "Word Processor", "Write letters and documents"),
            entry("firefox-esr.desktop", "Firefox ESR", "Web Browser", "Browse the World Wide Web"),
            entry("amade-web.desktop", "Web", "Web Browser", "A small browser"),
            entry("amade-terminal.desktop", "Terminal", "Terminal Emulator", "Use the command line"));

    private static double sum(String text) {
        return SearchCalculator.evaluate(text).orElseThrow(() -> new AssertionError(text));
    }

    @Test
    void sumsAreWorkedOut() {
        assertEquals(87, sum("12*7+3"));
        assertEquals(4.5, sum("(4+5)/2"));
        assertEquals(1024, sum("2^10"));
        assertEquals(512, sum("2^3^2"), "powers go from the right");
        assertEquals(12, sum("80*15%"));
        assertEquals(3.5, sum("1,5 + 2"), "a decimal comma");
        assertEquals(-6, sum("-(2*3)"));
        assertEquals(Math.sqrt(2), sum("sqrt(2)"));
        assertEquals(2 * Math.PI, sum("2*pi"));
        assertEquals(8, sum("= 2 × 4"));
    }

    @Test
    void wordsNumbersAndDatesAreNoSums() {
        for (String text : List.of("", "2024", "-5", "firefox", "notes-2", "5-", "1/0", "2026-11-01", "(1+2",
                "sqrt(-1)", "e+1")) {
            assertEquals(Optional.empty(), SearchCalculator.evaluate(text), text);
        }
    }

    @Test
    void resultsAreShortAndInTheLanguage() {
        assertEquals("0.333333333333", SearchCalculator.format(1.0 / 3, Locale.UK));
        assertEquals("3,5", SearchCalculator.format(3.5, Locale.GERMANY));
        assertEquals("87", SearchCalculator.format(87, Locale.UK));
        assertEquals("0.3", SearchCalculator.format(0.1 + 0.2, Locale.UK));
        assertEquals("1E+20", SearchCalculator.format(1e20, Locale.UK));
    }

    @Test
    void theBetterMatchScoresHigher() {
        assertEquals(100, SearchIndex.score("Web", "web"));
        assertEquals(80, SearchIndex.score("WritePad", "wri"));
        assertEquals(60, SearchIndex.score("Firefox ESR", "esr"));
        assertEquals(40, SearchIndex.score("WritePad", "pad"));
        assertEquals(0, SearchIndex.score("WritePad", "calc"));
    }

    @Test
    void programsAreFoundByNameFirstThenByWhatTheyAre() {
        List<SearchIndex.Hit> web = SearchIndex.programs("web", entries);
        assertEquals(List.of("Web", "Firefox ESR"), web.stream().map(SearchIndex.Hit::title).toList(),
                "the name before the generic name");
        assertEquals("Web Browser", web.get(1).detail());
        assertEquals("amade-writepad.desktop", SearchIndex.programs("word", entries).get(0).target());
        assertEquals("Terminal", SearchIndex.programs("command", entries).get(0).title(), "the description");
        assertTrue(SearchIndex.programs("  ", entries).isEmpty());
        List<XdgDesktopEntry> more = new java.util.ArrayList<>(entries);
        more.add(entry("vim.desktop", "Vim", "Text Editor", "Edit text files"));
        more.add(entry("amade-files.desktop", "amadeOS File Manager", "File Manager", "Browse the files"));
        assertEquals(List.of("amadeOS File Manager"), SearchIndex.programs("files", more).stream()
                .map(SearchIndex.Hit::title).toList(), "a word of a description only when nothing better is found");
    }

    @Test
    void settingsPagesAreFoundByTheirSearchWords() {
        List<SearchIndex.Hit> hits = SearchIndex.settings("wallpaper");
        assertFalse(hits.isEmpty());
        assertEquals("backdrop", hits.get(0).target());
        assertEquals("look", SearchIndex.settings("horizon").get(0).target());
        assertTrue(SearchIndex.settings("volume").stream().anyMatch(h -> h.target().equals("sound")));
    }

    @Test
    void filesAreFoundUnderHomeWithoutHiddenOnes() throws Exception {
        Files.createDirectories(home.resolve("Documents/Letters"));
        Files.createDirectories(home.resolve(".cache/report"));
        Files.createDirectories(home.resolve("project/node_modules/report"));
        Files.writeString(home.resolve("Documents/Letters/report-2026.odt"), "x");
        Files.writeString(home.resolve("report.txt"), "x");
        Files.writeString(home.resolve(".report"), "x");
        List<Path> index = SearchIndex.indexFiles(home, () -> false);
        assertFalse(index.stream().anyMatch(p -> p.toString().contains(".cache") || p.toString().contains("node_modules")
                || p.getFileName().toString().startsWith(".")), index.toString());

        List<SearchIndex.Hit> hits = SearchIndex.files("report", home, index);
        assertEquals(List.of("report.txt", "report-2026.odt"), hits.stream().map(SearchIndex.Hit::title).toList());
        assertEquals("~", hits.get(0).detail());
        assertEquals("~/Documents/Letters", hits.get(1).detail());
        assertEquals("folder", SearchIndex.files("letters", home, index).get(0).iconKey());
        assertTrue(SearchIndex.files("r", home, index).isEmpty(), "one letter is too little for files");
    }

    @Test
    void theSumComesFirstThenProgramsSettingsAndFiles() throws Exception {
        Files.writeString(home.resolve("terminal-notes.txt"), "x");
        List<SearchIndex.Hit> hits = HorizonSearch.search("terminal", entries, home,
                SearchIndex.indexFiles(home, () -> false));
        assertEquals(SearchIndex.Kind.PROGRAM, hits.get(0).kind());
        assertEquals(SearchIndex.Kind.FILE, hits.get(hits.size() - 1).kind());
        List<SearchIndex.Hit> sum = HorizonSearch.search("6*7", entries, home, List.of());
        assertEquals(SearchIndex.Kind.CALCULATION, sum.get(0).kind());
        assertEquals("42", sum.get(0).target());
        assertEquals(HorizonSearch.HEADING_HEIGHT + HorizonSearch.ROW_HEIGHT + 12, HorizonSearch.resultsHeight(sum));
    }
}
