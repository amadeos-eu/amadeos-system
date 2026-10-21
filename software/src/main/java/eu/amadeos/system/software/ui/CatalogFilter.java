package eu.amadeos.system.software.ui;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * What the catalog tabs show: packages whose names or summaries contain every word of the search, all of them
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

    /** Every word in the package name, the application's name or a summary. */
    boolean matches(Item item) {
        if (installedOnly && !item.entry().installed()) return false;
        if (words.isEmpty()) return true;
        String text = (item.name() + " " + item.title() + " " + item.summary() + " " + item.entry().summary())
                .toLowerCase(Locale.ROOT);
        for (String word : words) {
            if (!text.contains(word)) return false;
        }
        return true;
    }

    /**
     * The matching items in their order; with a search, a name that is the word comes first, then names
     * starting with it, then names containing it (package or application name, whichever ranks better).
     */
    List<Item> apply(List<Item> items) {
        List<Item> shown = items.stream().filter(this::matches).toList();
        if (words.isEmpty()) return shown;
        String first = words.getFirst();
        return shown.stream().sorted(Comparator.comparingInt((Item i) -> rank(i, first))
                .thenComparing(i -> i.title().toLowerCase(Locale.ROOT))).toList();
    }

    private static int rank(Item item, String word) {
        return Math.min(rank(item.name(), word), rank(item.title(), word));
    }

    private static int rank(String name, String word) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.equals(word)) return 0;
        if (n.startsWith(word)) return 1;
        return n.contains(word) ? 2 : 3;
    }
}
