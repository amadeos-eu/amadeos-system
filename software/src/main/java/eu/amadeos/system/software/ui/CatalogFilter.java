package eu.amadeos.system.software.ui;

import eu.amadeos.system.packages.CatalogEntry;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * What the catalog tabs show: packages whose name or summary contains every word of the search, all of them
 * or only the installed ones. With a search, the best name matches come first.
 */
record CatalogFilter(List<String> words, boolean installedOnly) {
    static final CatalogFilter ALL = new CatalogFilter(List.of(), false);

    CatalogFilter {
        words = List.copyOf(words);
    }

    static CatalogFilter of(String search, boolean installedOnly) {
        String text = search.strip().toLowerCase(Locale.ROOT);
        return new CatalogFilter(text.isEmpty() ? List.of() : List.of(text.split("\\s+")), installedOnly);
    }

    boolean matches(CatalogEntry entry) {
        if (installedOnly && !entry.installed()) return false;
        String name = entry.name().toLowerCase(Locale.ROOT);
        String summary = entry.summary().toLowerCase(Locale.ROOT);
        for (String word : words) {
            if (!name.contains(word) && !summary.contains(word)) return false;
        }
        return true;
    }

    /** The matching entries: by name without a search; with one, the name itself first, then names starting with it. */
    List<CatalogEntry> apply(List<CatalogEntry> entries) {
        List<CatalogEntry> shown = entries.stream().filter(this::matches).toList();
        if (words.isEmpty()) return shown;
        String first = words.getFirst();
        return shown.stream().sorted(Comparator.comparingInt((CatalogEntry e) -> rank(e, first))
                .thenComparing(CatalogEntry::name)).toList();
    }

    private static int rank(CatalogEntry entry, String word) {
        String name = entry.name().toLowerCase(Locale.ROOT);
        if (name.equals(word)) return 0;
        if (name.startsWith(word)) return 1;
        return name.contains(word) ? 2 : 3;
    }
}
