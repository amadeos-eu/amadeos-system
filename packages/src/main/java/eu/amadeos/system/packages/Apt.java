package eu.amadeos.system.packages;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The read-only apt and dpkg command lines the library uses and parsers for their output. Pure, so it can be
 * tested with recorded output instead of a Debian system. Commands run with {@code LC_ALL=C.UTF-8}, so the
 * parsers see apt's untranslated texts.
 */
public final class Apt {
    /** {@code Inst NAME [INSTALLED] (NEW ORIGIN, ORIGIN [ARCH])}, the installed version only for upgrades. */
    private static final Pattern INST = Pattern.compile(
            "^Inst (\\S+) (?:\\[([^]]*)] )?\\((\\S+) (.*) \\[([^]]+)]\\)");
    private static final Pattern REMV = Pattern.compile("^Remv (\\S+)(?: \\[([^]]*)])?");
    /** dpkg-query fields, separated by tabs. */
    private static final String INSTALLED_FORMAT =
            "${binary:Package}\\t${Version}\\t${db:Status-Abbrev}\\t${Installed-Size}\\t${Section}\\t${binary:Summary}\\n";

    private Apt() {
    }

    public static List<String> simulateUpgrade() {
        // The same upgrade as "amadeos-packages upgrade": new dependencies are installed, nothing is removed.
        return List.of("apt-get", "-s", "-o", "Debug::NoLocking=1", "--with-new-pkgs", "upgrade");
    }

    public static List<String> simulateInstall(List<String> names) {
        return withNames(List.of("apt-get", "-s", "-o", "Debug::NoLocking=1", "install"), names);
    }

    public static List<String> simulateRemove(List<String> names) {
        return withNames(List.of("apt-get", "-s", "-o", "Debug::NoLocking=1", "remove"), names);
    }

    public static List<String> dumpAvailable() {
        return List.of("apt-cache", "dumpavail");
    }

    public static List<String> show(String name) {
        return withNames(List.of("apt-cache", "show", "--no-all-versions"), List.of(name));
    }

    /** apt-cache takes the words as regular expressions; they are quoted, so "c++" means c++. */
    public static List<String> search(String words) {
        List<String> command = new ArrayList<>(List.of("apt-cache", "search"));
        for (String word : words.strip().split("\\s+")) {
            if (!word.isEmpty()) command.add(quoteRegex(word));
        }
        return List.copyOf(command);
    }

    public static List<String> installed() {
        return List.of("dpkg-query", "-W", "-f=" + INSTALLED_FORMAT);
    }

    /** The setting that makes Debian's apt-daily-upgrade timer run unattended-upgrades. */
    public static List<String> unattendedUpgrade() {
        return List.of("apt-config", "shell", "VALUE", "APT::Periodic::Unattended-Upgrade");
    }

    /**
     * {@code apt-config shell} output ({@code VALUE='1'}) as on or off. The value is a number of days or
     * "always"; empty and 0 mean off.
     */
    public static boolean parseUnattendedUpgrade(String output) {
        for (String line : output.lines().toList()) {
            if (!line.startsWith("VALUE=")) continue;
            String value = line.substring("VALUE=".length()).replace("'", "").strip();
            return !value.isEmpty() && !value.equals("0");
        }
        return false;
    }

    public static List<String> automatic() {
        return List.of("apt-mark", "showauto");
    }

    /**
     * Package names as Debian allows them, with an optional architecture. Checked before a name goes on a
     * command line, so a name can never be taken as an option; the helper checks again.
     */
    public static boolean validName(String name) {
        return name.matches("[a-z0-9][a-z0-9+.-]+(:[a-z0-9-]+)?");
    }

    /** The changes of a simulation, in apt's order (which installs dependencies first). */
    public static List<Change> parseSimulation(String output) {
        List<Change> changes = new ArrayList<>();
        for (String line : output.lines().toList()) {
            Matcher inst = INST.matcher(line);
            if (inst.find()) {
                String installed = inst.group(2) == null ? "" : inst.group(2);
                List<Origin> origins = new ArrayList<>();
                for (String origin : inst.group(4).split(", ")) {
                    if (!origin.isBlank()) origins.add(Origin.parse(origin));
                }
                changes.add(new Change(installed.isEmpty() ? Change.Kind.INSTALL : Change.Kind.UPGRADE,
                        inst.group(1), installed, inst.group(3), origins, inst.group(5)));
                continue;
            }
            Matcher remv = REMV.matcher(line);
            if (remv.find()) {
                changes.add(new Change(Change.Kind.REMOVE, remv.group(1), remv.group(2) == null ? "" : remv.group(2),
                        "", List.of(), ""));
            }
        }
        return changes;
    }

    /**
     * Splits a Debian control file (Packages index, {@code apt-cache show}) into its stanzas. Field names keep
     * their case; continuation lines are joined with '\n' and keep their leading space.
     */
    public static List<Map<String, String>> parseControl(String text) {
        List<Map<String, String>> stanzas = new ArrayList<>();
        Map<String, String> stanza = new LinkedHashMap<>();
        String field = null;
        for (String line : text.lines().toList()) {
            if (line.isBlank()) {
                if (!stanza.isEmpty()) stanzas.add(stanza);
                stanza = new LinkedHashMap<>();
                field = null;
            } else if ((line.startsWith(" ") || line.startsWith("\t")) && field != null) {
                stanza.merge(field, "\n" + line, String::concat);
            } else {
                int colon = line.indexOf(':');
                if (colon <= 0) continue;
                field = line.substring(0, colon);
                stanza.put(field, line.substring(colon + 1).strip());
            }
        }
        if (!stanza.isEmpty()) stanzas.add(stanza);
        return stanzas;
    }

    /** A stanza of a Packages index or of {@code apt-cache show} as a package. */
    public static PackageInfo toPackage(Map<String, String> stanza) {
        String description = stanza.getOrDefault("Description", stanza.getOrDefault("Description-en", ""));
        int newline = description.indexOf('\n');
        String summary = newline < 0 ? description : description.substring(0, newline);
        String longDescription = newline < 0 ? "" : longDescription(description.substring(newline + 1));
        String section = stanza.getOrDefault("Section", "");
        // "contrib/utils" -> "utils"
        section = section.substring(section.indexOf('/') + 1);
        return new PackageInfo(stanza.getOrDefault("Package", ""), stanza.getOrDefault("Version", ""),
                stanza.getOrDefault("Architecture", ""), section, summary.strip(), longDescription,
                parseLong(stanza.get("Installed-Size")), parseLong(stanza.get("Size")),
                stanza.getOrDefault("Homepage", ""), list(stanza.get("Depends")), list(stanza.get("Tag")));
    }

    public static List<PackageInfo> parsePackages(String text) {
        return parseControl(text).stream().filter(s -> s.containsKey("Package")).map(Apt::toPackage).toList();
    }

    /** {@code dpkg-query} output as installed packages; half-installed and removed ones are left out. */
    public static List<InstalledPackage> parseInstalled(String output, Set<String> automatic) {
        List<InstalledPackage> packages = new ArrayList<>();
        for (String line : output.lines().toList()) {
            String[] f = line.split("\t", -1);
            if (f.length < 6) continue;
            String status = f[2].strip();
            // ii installed, hi installed and held
            if (!status.equals("ii") && !status.equals("hi")) continue;
            String name = f[0];
            String plain = name.contains(":") ? name.substring(0, name.indexOf(':')) : name;
            boolean auto = automatic.contains(name) || automatic.contains(plain);
            packages.add(new InstalledPackage(name, f[1], f[4], f[5], parseLong(f[3]), auto));
        }
        return packages;
    }

    public static Set<String> parseAutomatic(String output) {
        Set<String> names = new HashSet<>();
        for (String line : output.lines().toList()) {
            if (!line.isBlank()) names.add(line.strip());
        }
        return names;
    }

    /** {@code apt-cache search} output: "name - summary". */
    public static List<PackageService.SearchHit> parseSearch(String output) {
        List<PackageService.SearchHit> hits = new ArrayList<>();
        for (String line : output.lines().toList()) {
            int dash = line.indexOf(" - ");
            if (dash > 0) hits.add(new PackageService.SearchHit(line.substring(0, dash), line.substring(dash + 3)));
        }
        return hits;
    }

    /** The continuation lines of a Description: one leading space removed, " ." becomes an empty line. */
    private static String longDescription(String lines) {
        List<String> out = new ArrayList<>();
        for (String line : lines.split("\n", -1)) {
            String text = line.startsWith(" ") ? line.substring(1) : line;
            out.add(text.equals(".") ? "" : text);
        }
        return String.join("\n", out).strip();
    }

    private static List<String> list(String value) {
        if (value == null) return List.of();
        List<String> items = new ArrayList<>();
        for (String item : value.split(",")) {
            if (!item.isBlank()) items.add(item.strip());
        }
        return items;
    }

    private static long parseLong(String value) {
        if (value == null) return 0;
        try {
            return Long.parseLong(value.strip());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static List<String> withNames(List<String> command, List<String> names) {
        List<String> all = new ArrayList<>(command);
        for (String name : names) {
            if (!validName(name)) throw new IllegalArgumentException("not a package name: " + name);
            all.add(name);
        }
        return List.copyOf(all);
    }

    private static String quoteRegex(String word) {
        StringBuilder quoted = new StringBuilder();
        for (char c : word.toCharArray()) {
            if ("\\.[]{}()*+?^$|".indexOf(c) >= 0) quoted.append('\\');
            quoted.append(c);
        }
        return quoted.toString();
    }
}
