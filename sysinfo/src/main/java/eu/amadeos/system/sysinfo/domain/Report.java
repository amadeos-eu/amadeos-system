package eu.amadeos.system.sysinfo.domain;

import java.util.List;

/** The information shown on the page: titled sections of labelled values, all texts already translated. */
public record Report(List<Section> sections) {
    public Report {
        sections = List.copyOf(sections);
    }

    public record Section(String title, List<Item> items) {
        public Section {
            items = List.copyOf(items);
        }
    }

    public record Item(String label, String value) {
    }

    /** The report as plain text, for the clipboard and text files: a heading per section, aligned values. */
    public String toText(String heading) {
        int width = sections.stream().flatMap(s -> s.items().stream())
                .mapToInt(i -> i.label().length()).max().orElse(0);
        StringBuilder text = new StringBuilder(heading).append('\n').append("=".repeat(heading.length())).append('\n');
        for (Section section : sections) {
            text.append('\n').append(section.title()).append('\n')
                    .append("-".repeat(section.title().length())).append('\n');
            for (Item item : section.items()) {
                text.append(item.label()).append(':')
                        .append(" ".repeat(width - item.label().length() + 2))
                        .append(item.value()).append('\n');
            }
        }
        return text.toString();
    }
}
